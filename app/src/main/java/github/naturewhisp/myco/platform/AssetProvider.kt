package github.naturewhisp.myco.platform

import android.content.Context
import java.io.InputStream

/**
 * Astrazione per l'accesso a risorse binarie e asset confezionati con l'applicazione.
 * Permette la separazione tra Android AssetManager e le risorse di sistema macOS (Bundle/Filesystem).
 */
fun interface AssetProvider {
    fun open(path: String): InputStream
}

/**
 * Implementazione Android di [AssetProvider] basata su [android.content.res.AssetManager].
 */
class AndroidAssetProvider(private val context: Context) : AssetProvider {
    override fun open(path: String): InputStream {
        return context.assets.open(path)
    }
}
