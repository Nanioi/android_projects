package com.nanioi.usedtransactionapp.home

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.ktx.storage
import com.nanioi.usedtransactionapp.DBKey.Companion.DB_ARTICLES
import com.nanioi.usedtransactionapp.R

class AddArticleActivity : AppCompatActivity() {

    private var selectedUri: Uri?=null

    private val auth:FirebaseAuth by lazy{
        Firebase.auth
    }
    private val storage:FirebaseStorage by lazy{
        Firebase.storage
    }
    private val articleDB:DatabaseReference by lazy{
        Firebase.database.reference.child(DB_ARTICLES)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_article_add)

        findViewById<Button>(R.id.imageAddButton).setOnClickListener {
            when{
                ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.READ_EXTERNAL_STORAGE
                )==PackageManager.PERMISSION_GRANTED->{ // 허용된경우
                    startContentProvider()
                }
                shouldShowRequestPermissionRationale(android.Manifest.permission.READ_EXTERNAL_STORAGE)->{ // 교육용 팝업이 필요한경우
                    showPermissionContextPopup()
                }else->{ // 그 외
                    requestPermissions(arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE),1010) // 해당권한 요청
                }
            }
        }

        findViewById<Button>(R.id.submitButton).setOnClickListener {
            val title = findViewById<EditText>(R.id.titleEditText).text.toString()
            val price = findViewById<EditText>(R.id.priceEditText).text.toString()
            val sellerId = auth.currentUser?.uid.orEmpty()

            showProgess()

            if(selectedUri != null){
                val photoUri = selectedUri ?: return@setOnClickListener // 널이면 리턴, if문으로 널처리 했지만 한번 더
                uploadPhoto(photoUri,  // 비동기
                    successHandler = { uri->
                        uploadArticle(sellerId,title,price,uri)
                    },
                    errorHandler = {
                        Toast.makeText(this,"사진 업로드에 실패하습니다.", Toast.LENGTH_SHORT).show()
                        hideProgess()
                    }
                )
            }else{ // 동기
                uploadArticle(sellerId,title,price,"")
            }
        }
    }
    //storage 사용
    private fun uploadPhoto(uri:Uri,successHandler:(String)->Unit,errorHandler :()->Unit){
        val fileName = "${System.currentTimeMillis()}.png"
        storage.reference.child("article/photo").child(fileName)
            .putFile(uri)
            .addOnCompleteListener{ // 성공했는지 확인 리스너
                if(it.isSuccessful){ // 성공시 -> 업로드 완료
                    storage.reference.child("article/photo").child(fileName)
                        .downloadUrl
                        .addOnSuccessListener { uri->
                            successHandler(uri.toString()) // downloadUrl을 잘 가져온 경우
                        }.addOnFailureListener {
                            errorHandler()
                        }
                }else{ // 업로드 실패
                    errorHandler()
                }
            }
    }
    private fun uploadArticle(sellerId:String, title:String,price:String,imageUrl: String){
        val model = ArticleModel(sellerId,title,System.currentTimeMillis(),"$price 원",imageUrl) // articleModel 클래스
        articleDB.push().setValue(model) // DB에 모델을 넣어줌

        hideProgess()
        finish()
    }

    override fun onRequestPermissionsResult( // 권한에 대한 결과가 오게 되면 이 함수 호출된다.
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when(requestCode){
            1010->
                if(grantResults.isNotEmpty()&& grantResults[0] == PackageManager.PERMISSION_GRANTED){ // 승낙된경우
                    startContentProvider()
                }else{
                    Toast.makeText(this,"권한을 거부하셨습니다.", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun showProgess(){
        findViewById<ProgressBar>(R.id.progessBar).isVisible = true
    }
    private fun hideProgess(){
        findViewById<ProgressBar>(R.id.progessBar).isVisible = false
    }

    //이미지 받아오기
    private fun startContentProvider() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "image/*" // 이미지타입만 가져오도록
        startActivityForResult(intent,2020) // 이미지 가져온 것에 대해 데이터 받아오기
    }

    //데이터 가져오기
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if(resultCode != Activity.RESULT_OK){
            return
        }
        when(requestCode){ // 2020일경우 data안 사진에 대한 uri 저장
            2020->{
                val uri = data?.data
                if(uri!=null){
                    findViewById<ImageView>(R.id.photoImageView).setImageURI(uri)
                    selectedUri = uri // 이미지 업로드 버튼을 눌러야 저장되므로 그전까지 이 변수에 저장
                }else{
                    Toast.makeText(this,"사진을 가져오지 못했습니다.", Toast.LENGTH_SHORT).show()
                }
            }
            else ->{
                Toast.makeText(this,"사진을 가져오지 못했습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }
    //교육용 팝업 구현 함수
    private fun showPermissionContextPopup() {
        AlertDialog.Builder(this)
            .setTitle("권한이 필요합니다.")
            .setMessage("사진을 가져오기 위한 권한이 필요합니다.")
            .setPositiveButton("동의"){_,_ ->
                requestPermissions(arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE),1010)
            }
            .create()
            .show()
    }

}