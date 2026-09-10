package github.naturewhisp.myco.platform.android

import android.content.Context
import github.naturewhisp.myco.platform.AssetProvider
import java.io.InputStream

/**
 * Implementazione Android di [AssetProvider] basata su [android.content.res.AssetManager].
 *
 * @param context Contesto Android dell'applicazione per accedere alla cartella asset.
 */
class AndroidAssetProvider(private val context: Context) : AssetProvider {
    override fun open(path: String): InputStream {
        return context.assets.open(path)
    }
}
