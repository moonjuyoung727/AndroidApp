package com.example.androidapplication.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

import com.example.androidapplication.repository.AuthRepository
import com.example.androidapplication.storage.AuthPrefs

class AuthViewModelFactory(
    private val repository: AuthRepository,
    private val authPrefs: AuthPrefs
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(
        modelClass: Class<T>
    ): T {

        return when {

            modelClass.isAssignableFrom(
                LoginViewModel::class.java
            ) -> {

                @Suppress("UNCHECKED_CAST")
                LoginViewModel(repository, authPrefs) as T
            }


            modelClass.isAssignableFrom(
                SignupViewModel::class.java
            ) -> {

                @Suppress("UNCHECKED_CAST")
                SignupViewModel(repository) as T
            }


            else -> {
                throw IllegalArgumentException(
                    "Unknown ViewModel class: ${modelClass.name}"
                )
            }
        }
    }
}