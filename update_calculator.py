import re

with open("app/src/main/java/com/example/utils/CacheCalculator.kt", "r") as f:
    content = f.read()

func = """    fun clearAppCache(context: Context) {
        try {
            val cacheDir = context.cacheDir
            if (cacheDir != null && cacheDir.exists()) {
                deleteRecursively(cacheDir)
            }
            val filesDir = context.filesDir
            if (filesDir != null && filesDir.exists()) {
                deleteRecursively(filesDir)
            }
            val externalCacheDir = context.externalCacheDir
            if (externalCacheDir != null && externalCacheDir.exists()) {
                deleteRecursively(externalCacheDir)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun deleteRecursively(file: File) {
        if (file.isDirectory) {
            file.listFiles()?.forEach { deleteRecursively(it) }
        }
        file.delete()
    }
"""

# Insert before getFolderSize
idx = content.find('    private fun getFolderSize')
if idx != -1:
    content = content[:idx] + func + '\n' + content[idx:]
    with open("app/src/main/java/com/example/utils/CacheCalculator.kt", "w") as f:
        f.write(content)
