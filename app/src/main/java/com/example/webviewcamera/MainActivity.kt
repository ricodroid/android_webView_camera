package com.example.webviewcamera

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.qiitacamera.R
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date


class MainActivity : AppCompatActivity() {
    /** WebView */
    private lateinit var webView: WebView

    /** 画像のパスを保存する */
    private var mCameraPhotoPath: String? = null

    /** ファイル選択ダイアログが表示された際に、選択されたファイルのURLを受け取る
     * アップロードされたファイルの取得や操作する
     * */
    private var mUM: ValueCallback<Uri>? = null

    /** ファイル選択ダイアログが表示された際に、選択されたファイルのURLを受け取る
     * 複数のファイルが選択された場合
     *  */
    private var mUMA: ValueCallback<Array<Uri>>? = null

    /** ファイル選択リクエストの識別子 なんでもOK */
    private val FCR = 1

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == FCR) {
            if (mUMA != null) {
                val resultUri: Uri? = if (data?.data == null) {
                    if (mCameraPhotoPath != null) {
                        Uri.parse(mCameraPhotoPath)
                    } else {
                        null
                    }
                } else {
                    data.data
                }

                val results = if (resultUri != null) {
                    arrayOf(resultUri)
                } else {
                    null
                }

                mUMA?.onReceiveValue(results)
                mUMA = null
            } else if (requestCode == FCR && mUM != null) {
                val result: Uri? = if (resultCode != Activity.RESULT_OK || data == null) {
                    null
                } else {
                    data.data
                }

                mUM?.onReceiveValue(result)
                mUM = null
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (Build.VERSION.SDK_INT >= 23 && (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.CAMERA
                    ) != PackageManager.PERMISSION_GRANTED)
        ) {
            ActivityCompat.requestPermissions(
                this@MainActivity, arrayOf(
                    Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.CAMERA
                ), 1
            )
        }

        // webViewのセット
        webView = findViewById<View>(R.id.web_view) as WebView
        val webSettings = webView.settings
        // WebView内でJavaScriptを有効にするための設定
        webSettings.javaScriptEnabled = true
        // WebViewがファイルへのアクセスを許可するための設定
        webSettings.allowFileAccess = true

        // WebViewをより安全に使いつつ、パフォーマンスも向上するためのの設定たち
        webSettings.mixedContentMode = 0
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        // WebView内のUIイベントを処理
        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                if (mUMA != null) {
                    mUMA!!.onReceiveValue(null)
                }

                mUMA = filePathCallback
                var takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)

                if (takePictureIntent.resolveActivity(this@MainActivity.packageManager) != null) {
                    var photoUri: Uri? = null
                    try {
                        photoUri = createImageFile()
                        takePictureIntent.putExtra("PhotoPath", mCameraPhotoPath)
                    } catch (ex: IOException) {
                        android.util.Log.e("WebActivity", "Image file creation failed", ex)
                    }
                    // ファイルが正常に作成された場合にのみ続行
                    if (photoUri != null) {
                        mCameraPhotoPath = photoUri.toString()
                        takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
                    } else {
                        takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, "null")
                    }
                }

                val contentSelectionIntent = Intent(Intent.ACTION_GET_CONTENT)
                contentSelectionIntent.addCategory(Intent.CATEGORY_OPENABLE)
                contentSelectionIntent.type = "*/*"

                var intentArray: Array<Intent?>

                if (takePictureIntent != null) {
                    intentArray = arrayOf(takePictureIntent)
                } else {
                    intentArray = arrayOfNulls(0)
                }

                val chooserIntent = Intent(Intent.ACTION_CHOOSER)
                chooserIntent.putExtra(Intent.EXTRA_INTENT, contentSelectionIntent)
                chooserIntent.putExtra(Intent.EXTRA_TITLE, "Image Chooser")
                chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, intentArray)
                startActivityForResult(chooserIntent, FCR)
                return true;
            }
        }

        // WebView内のページの読み込みとかのイベントを処理
        webView.webViewClient = object : WebViewClient() {
            override fun onReceivedError(
                view: WebView?,
                errorCode: Int,
                description: String?,
                failingUrl: String?
            ) {
                Toast.makeText(
                    applicationContext, "Failed loading app!",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        // 表示させたいwebのURL (私はDockerでローカルにたちあげているのでエミュレーターが参照するlocal)
        webView.loadUrl("http://10.0.2.2:8000/")
    }

    // insert()メソッドを使って画像ファイルのメタデータを含むContentValuesを使用して、
    // 外部ストレージの画像コンテンツURI（MediaStore.Images.Media.EXTERNAL_CONTENT_URI）に新しい画像ファイルを挿入する。
    private fun createImageFile(): Uri? {
        // Create an image file name
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val imageFileName = "JPEG_" + timeStamp + "_"

        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$imageFileName.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
        }

        val resolver = applicationContext.contentResolver
        val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

        return imageUri
    }
}