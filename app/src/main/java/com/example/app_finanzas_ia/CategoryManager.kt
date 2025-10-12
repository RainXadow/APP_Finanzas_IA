package com.example.app_finanzas_ia

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

class CategoryManager(private val context: Context) {

    private val gson = Gson()
    private val categoriesFile = "categories.json"
    private val rulesFile = "categorization_rules.json"

    /**
     * Obtiene todas las categorías
     */
    fun getAllCategories(): List<Category> {
        val file = File(context.filesDir, categoriesFile)
        if (!file.exists()) {
            // Crear categorías por defecto
            val defaultCategories = getDefaultCategories()
            saveCategories(defaultCategories)
            return defaultCategories
        }

        return try {
            val json = file.readText()
            val type = object : TypeToken<List<Category>>() {}.type
            gson.fromJson(json, type) ?: getDefaultCategories()
        } catch (e: Exception) {
            getDefaultCategories()
        }
    }

    /**
     * Guarda una nueva categoría
     */
    fun addCategory(name: String): Category {
        val categories = getAllCategories().toMutableList()
        val newCategory = Category(name = name)
        categories.add(newCategory)
        saveCategories(categories)
        return newCategory
    }

    /**
     * Elimina una categoría
     */
    fun deleteCategory(categoryName: String) {
        val categories = getAllCategories().filter { it.name != categoryName }
        saveCategories(categories)
    }

    /**
     * Obtiene todas las reglas de categorización
     */
    fun getAllRules(): Map<String, String> {
        val file = File(context.filesDir, rulesFile)
        if (!file.exists()) {
            return emptyMap()
        }

        return try {
            val json = file.readText()
            val type = object : TypeToken<Map<String, String>>() {}.type
            gson.fromJson(json, type) ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /**
     * Guarda una regla de categorización
     */
    fun saveRule(concept: String, categoryName: String) {
        val rules = getAllRules().toMutableMap()
        val normalizedConcept = normalizeConcept(concept)
        rules[normalizedConcept] = categoryName
        saveRules(rules)
    }

    /**
     * Busca la categoría automática para un concepto
     */
    fun findCategoryForConcept(concept: String): String? {
        val normalizedConcept = normalizeConcept(concept)
        val rules = getAllRules()

        // Búsqueda exacta
        if (rules.containsKey(normalizedConcept)) {
            return rules[normalizedConcept]
        }

        // Búsqueda por similitud
        val conceptLower = normalizedConcept.lowercase()
        for ((ruleConcept, category) in rules) {
            if (conceptLower.contains(ruleConcept.lowercase()) ||
                ruleConcept.lowercase().contains(conceptLower)) {
                return category
            }
        }

        // Búsqueda por palabras clave en categorías
        val categories = getAllCategories()
        for (category in categories) {
            for (keyword in category.keywords) {
                if (conceptLower.contains(keyword.lowercase())) {
                    return category.name
                }
            }
        }

        return null
    }

    /**
     * Normaliza un concepto para comparación
     */
    private fun normalizeConcept(concept: String): String {
        return concept.trim()
            .replace(Regex("""\s+"""), " ")
            .replace(Regex("""\d+"""), "") // Eliminar números
            .trim()
    }

    private fun getDefaultCategories(): List<Category> {
        return listOf(
            Category(name = "Alimentación", keywords = mutableSetOf("mercadona", "carrefour", "supermercado", "lidl", "aldi", "consum"), isDefault = true),
            Category(name = "Restaurantes", keywords = mutableSetOf("restaurante", "bar", "pizzeria", "burger", "comida", "dominos"), isDefault = true),
            Category(name = "Transporte", keywords = mutableSetOf("gasolina", "repsol", "cepsa", "plenoil", "plenergy", "uber", "cabify"), isDefault = true),
            Category(name = "Ocio", keywords = mutableSetOf("cine", "amazon", "netflix", "spotify", "steam"), isDefault = true),
            Category(name = "Servicios", keywords = mutableSetOf("recibo", "domiciliación", "digi", "movistar", "endesa"), isDefault = true),
            Category(name = "Transferencias", keywords = mutableSetOf("transferencia", "traspaso"), isDefault = true),
            Category(name = "Bizum", keywords = mutableSetOf("bizum"), isDefault = true),
            Category(name = "Compras Online", keywords = mutableSetOf("paypal", "amazon", "aliexpress", "alipay"), isDefault = true),
            Category(name = "Cajero", keywords = mutableSetOf("cajero", "atm", "ingreso anonimo"), isDefault = true),
            Category(name = "Nómina", keywords = mutableSetOf("nomina", "abono nomina"), isDefault = true),
            Category(name = "Sin categoría", isDefault = true)
        )
    }

    private fun saveCategories(categories: List<Category>) {
        val file = File(context.filesDir, categoriesFile)
        val json = gson.toJson(categories)
        file.writeText(json)
    }

    private fun saveRules(rules: Map<String, String>) {
        val file = File(context.filesDir, rulesFile)
        val json = gson.toJson(rules)
        file.writeText(json)
    }
}