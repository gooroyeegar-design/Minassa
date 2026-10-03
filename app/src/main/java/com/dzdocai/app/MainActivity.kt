package com.dzdocai.app
import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import java.util.concurrent.Executors
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

class MainActivity:ComponentActivity(){
 private val permission=registerForActivityResult(ActivityResultContracts.RequestPermission()){ }
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)permission.launch(Manifest.permission.CAMERA);setContent{DZGuideApp()}}
}
data class ScanResult(val type:String,val title:String,val confidence:String,val details:List<String>,val actions:List<String>)
@Composable fun DZGuideApp(){var q by remember{mutableStateOf("")};var r by remember{mutableStateOf<ScanResult?>(null)};val examples=listOf("Receipt","Book page","Birth certificate","National ID","Passport","Driving licence","Administrative form");MaterialTheme{Scaffold(topBar={TopAppBar(title={Text("DZ Guide AI")})}){p->LazyColumn(Modifier.padding(p).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){item{Text("Your Algerian document & object assistant",style=MaterialTheme.typography.headlineSmall)};item{Text("Scan something. Identify it. Understand it. Get the current official next step.")};item{OutlinedTextField(q,{q=it},Modifier.fillMaxWidth(),label={Text("Ask about a document")})};item{Button({r=DemoEngine.answer(q)},Modifier.fillMaxWidth()){Text("Analyze")}};item{OutlinedButton({r=DemoEngine.answer("receipt")},Modifier.fillMaxWidth()){Text("Simulate scan")}};item{Text("Try these",style=MaterialTheme.typography.titleMedium)};items(examples){e->AssistChip({r=DemoEngine.answer(e)},{Text(e)})};r?.let{x->item{HorizontalDivider()};item{Text(x.title,style=MaterialTheme.typography.headlineSmall)};item{Text("Type: "+x.type+" • "+x.confidence)};item{Text("What I found",style=MaterialTheme.typography.titleMedium)};items(x.details){Text("• $it")};item{Text("What to do next",style=MaterialTheme.typography.titleMedium)};items(x.actions){Text("→ $it")};item{Text("Official-source rule: verify current government procedures before acting.",style=MaterialTheme.typography.bodySmall)}}}}}}
object DemoEngine{fun answer(q:String):ScanResult{val x=q.lowercase();return when{ "receipt" in x->ScanResult("Purchase receipt","This looks like a receipt","Demo / high confidence",listOf("The production scanner will extract seller, date, items, total and warranty clues."),listOf("Keep the original for returns or warranty.","The app can save it locally and remind you about detected warranty dates."));"book" in x||"page" in x->ScanResult("Book / book page","This looks like a book page","Demo / medium confidence",listOf("OCR plus catalog matching will identify title, author, edition and likely page number."),listOf("Capture a clear page; include title/author when possible.","The app identifies the work without reproducing copyrighted text."));"birth" in x||"naissance" in x->ScanResult("Civil-status document","Possible birth-certificate document","Demo / medium confidence",listOf("The Interior Ministry currently lists online civil-status services for birth, marriage and death certificates."),listOf("Check the official Interior Ministry civil-status service.","If your case requires an in-person step, the production app will say where."));"passport" in x->ScanResult("Biometric passport","Possible passport-related document","Demo / medium confidence",listOf("The Interior Ministry portal lists biometric-document services and request tracking."),listOf("Use the official Interior Ministry service for the current procedure.","The app will show the current link and any in-person step."));"id" in x||"identity" in x||"cnibe" in x->ScanResult("National identity document","Possible CNIBE-related document","Demo / medium confidence",listOf("The Interior Ministry lists electronic/biometric identity services and CNIBE reading."),listOf("Use the official Interior Ministry portal for application or tracking.","Do not upload sensitive identity documents to untrusted services."));else->ScanResult("Unknown / needs scan","I need the image to identify this accurately","Not enough evidence",listOf("The production camera pipeline combines OCR, barcode scanning, image labels and AI classification."),listOf("Scan all edges in good light.","The app will show confidence and official sources rather than pretending certainty." ) )}}}


@Composable private fun ScannerView(onResult:(ScanResult)->Unit){
 val context=LocalContext.current
 val owner=context as ComponentActivity
 val previewView=remember{PreviewView(context)}
 val executor=remember{Executors.newSingleThreadExecutor()}
 DisposableEffect(Unit){
  val future=ProcessCameraProvider.getInstance(context)
  future.addListener({
   val provider=future.get()
   val preview=Preview.Builder().build().also{it.surfaceProvider=previewView.surfaceProvider}
   val analysis=ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
   analysis.setAnalyzer(executor){proxy->
    val media=proxy.image
    if(media==null){proxy.close();return@setAnalyzer}
    val image=InputImage.fromMediaImage(media,proxy.imageInfo.rotationDegrees)
    TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(image)
      .addOnSuccessListener{t->if(t.text.isNotBlank())onResult(DemoEngine.answer(t.text.take(1000)))}
      .addOnCompleteListener{proxy.close()}
    BarcodeScanning.getClient().process(image).addOnSuccessListener{codes->
      if(codes.isNotEmpty())onResult(DemoEngine.answer("barcode "+(codes.first().rawValue ?: "")))
    }
   }
   try{provider.unbindAll();provider.bindToLifecycle(owner,CameraSelector.DEFAULT_BACK_CAMERA,preview,analysis)}catch(_:Exception){}
  },ContextCompat.getMainExecutor(context))
  onDispose{executor.shutdown()}
 }
 AndroidView({previewView},Modifier.fillMaxWidth().height(360.dp))
}
