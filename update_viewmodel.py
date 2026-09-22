import re

with open("app/src/main/java/com/example/ui/AppViewModel.kt", "r") as f:
    content = f.read()

replacement = """    fun clearCache(selectedCategoryNames: List<String>) {
        val currentStats = _storageStats.value
        val newCategories = currentStats.categories.map { category ->
            if (category.subCategories != null) {
                val newSubCategories = category.subCategories.map { sub ->
                    if (selectedCategoryNames.contains(sub.categoryName)) {
                        sub.copy(sizeBytes = 0L)
                    } else {
                        sub
                    }
                }
                category.copy(
                    sizeBytes = newSubCategories.sumOf { it.sizeBytes },
                    subCategories = newSubCategories
                )
            } else {
                if (selectedCategoryNames.contains(category.categoryName)) {
                    category.copy(sizeBytes = 0L)
                } else {
                    category
                }
            }
        }
        _storageStats.value = currentStats.copy(categories = newCategories)
    }"""

start_idx = content.find('    fun clearCache(')
end_idx = content.find('    fun toggleStorageCategory(')
if start_idx != -1 and end_idx != -1:
    content = content[:start_idx] + replacement + '\n\n' + content[end_idx:]
    with open("app/src/main/java/com/example/ui/AppViewModel.kt", "w") as f:
        f.write(content)
