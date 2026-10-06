package com.example.carbrowser

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.SearchTemplate
import androidx.car.app.model.Template

/**
 * Text entry on the car screen via the host's own keyboard (available while parked).
 * The submitted query is returned to BrowserScreen through setResult().
 */
class SearchScreen(carContext: CarContext) : Screen(carContext) {

    override fun onGetTemplate(): Template =
        SearchTemplate.Builder(object : SearchTemplate.SearchCallback {
            override fun onSearchSubmitted(searchText: String) {
                val query = searchText.trim()
                if (query.isNotEmpty()) setResult(query)
                screenManager.pop()
            }
        })
            .setHeaderAction(Action.BACK)
            .setSearchHint("Search YouTube")
            .setShowKeyboardByDefault(true)
            .build()
}
