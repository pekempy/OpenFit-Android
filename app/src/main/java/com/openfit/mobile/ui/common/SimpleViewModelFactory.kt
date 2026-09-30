package com.openfit.mobile.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/** Manual DI's ViewModel counterpart: builds a ViewModel from a lambda that
 * closes over whatever it needs from AppContainer, instead of a Hilt
 * @HiltViewModel + generated factory. */
class SimpleViewModelFactory(private val creator: () -> ViewModel) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = creator() as T
}
