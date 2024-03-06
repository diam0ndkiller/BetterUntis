package com.sapuseven.untis.ui.activities

import android.os.Bundle
import android.util.Log
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.os.bundleOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sapuseven.untis.activities.LoginActivity.Companion.EXTRA_BOOLEAN_SHOW_BACK_BUTTON
import com.sapuseven.untis.activities.LoginDataInputActivity.Companion.EXTRA_BOOLEAN_DEMO_LOGIN
import com.sapuseven.untis.activities.LoginDataInputActivity.Companion.EXTRA_STRING_SCHOOL_INFO
import com.sapuseven.untis.api.client.SchoolSearchApi
import com.sapuseven.untis.api.model.untis.SchoolInfo
import com.sapuseven.untis.helpers.ErrorMessageDictionary
import com.sapuseven.untis.helpers.SerializationUtils.getJSON
import com.sapuseven.untis.services.CodeScanService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
	val schoolSearchApi: SchoolSearchApi,
	val codeScanService: CodeScanService,
	savedStateHandle: SavedStateHandle
) : ViewModel() {
	val debounceMillis: Long = 500

	var searchMode by mutableStateOf(false)
		private set

	var shouldShowBackButton = derivedStateOf {
		searchMode || savedStateHandle.get<Boolean>(EXTRA_BOOLEAN_SHOW_BACK_BUTTON) ?: false
	}

	var schoolSearchText by mutableStateOf("")
		private set

	var schoolSearchItems by mutableStateOf<List<SchoolInfo>>(emptyList())
		private set

	var schoolSearchError: Int? by mutableStateOf(null)
		private set

	var schoolSearchErrorRaw: String? by mutableStateOf(null)
		private set

	var schoolSearchLoading by mutableStateOf(false)
		private set

	var schoolSearchJob by mutableStateOf<Job?>(null)
		private set

	val events = MutableSharedFlow<LoginEvents>()

	fun onSchoolSearchFocusChanged(focused: Boolean) {
		if (focused) searchMode = true
	}

	private fun updateSearchMode(enabled: Boolean) {
		searchMode = enabled
		if (!enabled) {
			viewModelScope.launch {
				events.emit(LoginEvents.ClearFocus)
				schoolSearchText = ""
			}
		}
	}

	fun goBack(): Boolean {
		if (searchMode) {
			updateSearchMode(false)
			return false
		} else {
			return true
		}
	}

	fun updateSchoolSearchText(text: String) {
		schoolSearchText = text
	}

	fun startSchoolSearch() {
		schoolSearchError = null
		schoolSearchErrorRaw = null
		schoolSearchItems = emptyList()

		schoolSearchJob?.cancel()
		schoolSearchJob = viewModelScope.launch {
			if (schoolSearchText.isEmpty()) return@launch

			schoolSearchLoading = true
			delay(debounceMillis)
			try {
				val schoolSearchResult = schoolSearchApi.searchSchools(schoolSearchText)

				schoolSearchLoading = false

				schoolSearchResult.fold({
					schoolSearchItems = it.schools
				}, {
					schoolSearchError =
						ErrorMessageDictionary.getErrorMessageResource(it.code, false)
					schoolSearchErrorRaw = it.message.orEmpty()
				})
			} catch (e: Exception) {
				Log.e("LoginViewModel", "schoolSearch error", e)
			}
		}
	}

	fun stopSchoolSearch() {
		schoolSearchJob?.cancel()
	}

	// onClick listeners
	fun onSchoolSelected(school: SchoolInfo) {
		viewModelScope.launch {
			events.emit(
				LoginEvents.StartLoginActivity(
					extras = bundleOf(
						EXTRA_STRING_SCHOOL_INFO to getJSON().encodeToString(
							school
						)
					)
				)
			)
		}
	}

	fun onCodeScanClick() {
		codeScanService.scanCode {
			viewModelScope.launch {
				events.emit(LoginEvents.StartLoginActivity(data = it))
			}
		}
	}

	fun onDemoClick() {
		viewModelScope.launch {
			val bundle = Bundle().apply {
				//TODO add putBackgroundColorExtra(this)
				putBoolean(EXTRA_BOOLEAN_DEMO_LOGIN, true)
			}
			events.emit(LoginEvents.StartLoginActivity(extras = bundle))
		}
	}

	fun onManualDataInputClick() {
		viewModelScope.launch {
			val bundle = Bundle().apply {
				//TODO add putBackgroundColorExtra(this)
			}
			events.emit(LoginEvents.StartLoginActivity(extras = bundle))
		}
	}
}
