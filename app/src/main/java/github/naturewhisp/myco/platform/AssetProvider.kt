package github.naturewhisp.myco.platform

import java.io.InputStream

/**
 * Astrazione per l'accesso a risorse binarie e asset confezionati con l'applicazione.
 *
 * Permette la separazione tra l'AssetManager di Android e le risorse di sistema macOS (Bundle/Filesystem).
 */
fun interface AssetProvider {
    /**
     * Apre un flusso di input per l'asset identificato dal percorso specificato.
     *
     * @param path Percorso relativo dell'asset all'interno del pacchetto (es. "spun/spun_italy.bin").
     * @return [InputStream] per la lettura sequenziale del file binario.
     */
    fun open(path: String): InputStream
}
