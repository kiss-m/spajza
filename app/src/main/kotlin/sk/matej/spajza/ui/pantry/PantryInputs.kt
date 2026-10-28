package sk.matej.spajza.ui.pantry

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

/**
 * Starts the Google Play services barcode scanner: it brings its own camera screen, so the app
 * needs no camera permission. Returns the function that opens it.
 */
@Composable
fun rememberBarcodeScanner(onScanned: (String) -> Unit, onFailed: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val scanned by rememberUpdatedState(onScanned)
    val failed by rememberUpdatedState(onFailed)
    return remember(context) {
        {
            val options = GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E)
                .build()
            GmsBarcodeScanning.getClient(context, options).startScan()
                .addOnSuccessListener { barcode ->
                    val value = barcode.rawValue
                    if (value.isNullOrBlank()) failed() else scanned(value)
                }
                .addOnFailureListener { failed() }
        }
    }
}
