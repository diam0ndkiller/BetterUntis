package com.sapuseven.untis.ui.activities

import android.net.Uri
import android.util.Patterns
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.sapuseven.untis.activities.LoginDataInputActivity.Companion.DEMO_API_URL
import com.sapuseven.untis.activities.LoginDataInputActivity.Companion.EXTRA_BOOLEAN_DEMO_LOGIN
import com.sapuseven.untis.activities.LoginDataInputActivity.Companion.EXTRA_BOOLEAN_PROFILE_UPDATE
import com.sapuseven.untis.activities.LoginDataInputActivity.Companion.EXTRA_STRING_SCHOOL_INFO
import com.sapuseven.untis.activities.SAVED_STATE_INTENT_DATA
import com.sapuseven.untis.api.client.UserDataApi
import com.sapuseven.untis.api.model.untis.SchoolInfo
import com.sapuseven.untis.helpers.SerializationUtils.getJSON
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class LoginDataInputViewModel @Inject constructor(
	val userDataApi: UserDataApi,
	savedStateHandle: SavedStateHandle
) : ViewModel() {
	val loginData = LoginData()

	var advanced by mutableStateOf(
		loginData.proxyUrl.value?.isNotEmpty() == true ||
			loginData.apiUrl.value?.isNotEmpty() == true
	)

	var validate by mutableStateOf(false)
		private set

	var loading by mutableStateOf(false)
		private set

	var isExistingUser by mutableStateOf(false)
		private set

	var showQrCodeErrorDialog by mutableStateOf(false)
		private set

	val showProfileUpdate = savedStateHandle.get<Boolean>(EXTRA_BOOLEAN_PROFILE_UPDATE) == true

	val schoolIdValid = derivedStateOf {
		loginData.schoolId.value?.let {
			it.isNotEmpty()
		} ?: false
	}

	val usernameValid = derivedStateOf {
		loginData.username.value?.let {
			it.isNotEmpty()
		} ?: false || (loginData.anonymous.value == true)
	}

	val proxyUrlValid = derivedStateOf {
		loginData.proxyUrl.value?.let {
			it.isEmpty() || Patterns.WEB_URL.matcher(it).matches()
		} ?: true
	}

	val apiUrlValid = derivedStateOf {
		loginData.apiUrl.value?.let {
			it.isEmpty() || Patterns.WEB_URL.matcher(it).matches()
		} ?: true
	}

	init {
		if (savedStateHandle.get<Boolean>(EXTRA_BOOLEAN_DEMO_LOGIN) == true) {
			loginData.anonymous.value = true
			loginData.schoolId.value = "demo"
			advanced = true
			loginData.apiUrl.value = DEMO_API_URL

			loadData()
		}

		savedStateHandle.get<String>(EXTRA_STRING_SCHOOL_INFO)?.let {
			loginData.schoolId.value =
				getJSON().decodeFromString<SchoolInfo>(it).schoolId.toString()
			//schoolIdLocked = true
		}

		if (showProfileUpdate) {
			loadData()
		}

		try {
			loadFromAppLinkData(savedStateHandle.get<String>(SAVED_STATE_INTENT_DATA))
		} catch (e: Exception) {
			// TODO handle correctly
			showQrCodeErrorDialog = true
		}
	}

	fun onLoginClick() {
		validate = true
		if (schoolIdValid.value && usernameValid.value && proxyUrlValid.value && apiUrlValid.value) {
			//snackbarHostState.currentSnackbarData?.dismiss()
			loadData()
		}
	}

	private fun loadFromAppLinkData(appLinkDataString: String?) {
		if (appLinkDataString == null)
			return;

		val appLinkData = Uri.parse(appLinkDataString)

		if (appLinkData?.isHierarchical == true && appLinkData.scheme == "untis" && appLinkData.host == "setschool") {
			// Untis-native values
			loginData.schoolId.value = appLinkData.getQueryParameter("school")
			loginData.username.value = appLinkData.getQueryParameter("user")
			loginData.password.value = appLinkData.getQueryParameter("key")

			// Custom values
			loginData.anonymous.value = appLinkData.getBooleanQueryParameter("anonymous", false)
			loginData.proxyUrl.value = appLinkData.getQueryParameter("proxyUrl") ?: ""
			loginData.apiUrl.value = appLinkData.getQueryParameter("apiUrl")
			loginData.skipAppSecret.value =
				appLinkData.getBooleanQueryParameter("skipAppSecret", false)

			advanced =
				loginData.proxyUrl.value?.isNotEmpty() == true || loginData.apiUrl.value?.isNotEmpty() == true
		} else {
			showQrCodeErrorDialog = true
		}
	}

	private fun loadData() {
		loading = true

		/*viewModelScope.launch {
			LoginHelper(
				loginData = LoginDataInfo(
					username.value ?: "",
					password.value ?: existingUser?.key ?: "",
					anonymous.value ?: false
				),
				proxyHost = proxyUrl.value,
				onStatusUpdate = { status ->
					Log.d(
						LoginDataInputActivity::class.java.simpleName,
						getString(status)
					)
				},
				onError = { error ->
					val errorMessage = when {
						error.errorCode != null -> ErrorMessageDictionary.getErrorMessage(
							resources,
							error.errorCode,
							error.errorMessage
						)

						error.errorMessageStringRes != null -> getString(
							error.errorMessageStringRes,
							error.errorMessage
						)

						else -> error.errorMessage
							?: getString(R.string.all_error)
					}

					loading = false
					coroutineScope.launch {
						snackbarHostState.showSnackbar(
							errorMessage,
							duration = SnackbarDuration.Long
						)
					}
				}).run {
				val schoolInfo = (
					when {
						schoolInfoFromSearch != null -> schoolInfoFromSearch
						advanced && !apiUrl.value.isNullOrBlank() -> SchoolInfo(
							server = "",
							useMobileServiceUrlAndroid = true,
							useMobileServiceUrlIos = true,
							address = "",
							displayName = schoolId.value ?: "",
							loginName = schoolId.value ?: "",
							schoolId = schoolId.value?.toIntOrNull()
								?: 0,
							serverUrl = apiUrl.value ?: "",
							mobileServiceUrl = apiUrl.value
						)

						else -> loadSchoolInfo(
							schoolId.value ?: ""
						)
					}) ?: return@run
				val untisApiUrl =
					if (advanced && !apiUrl.value.isNullOrBlank())
						apiUrl.value ?: ""
					else if (schoolInfo.useMobileServiceUrlAndroid && !schoolInfo.mobileServiceUrl.isNullOrBlank()) schoolInfo.mobileServiceUrl!!
					else Uri.parse(schoolInfo.serverUrl).buildUpon()
						.appendEncodedPath("jsonrpc_intern.do")
						.build().toString()
				val appSharedSecret =
					when {
						loginData.anonymous -> ""
						skipAppSecret.value == true -> loginData.password
						else -> loadAppSharedSecret(untisApiUrl)
							?: return@run
					}
				val userDataResponse =
					loadUserData(untisApiUrl, appSharedSecret)
						?: return@run
				val bookmarks =
					existingUserId?.let { user ->
						userDatabase.userDao().getById(user)?.bookmarks?.toSet()
					}
						?: emptySet()
				var userId = existingUserId ?: 0
				val user = User(
					userId,
					profileName.value ?: "",
					untisApiUrl,
					schoolInfo.schoolId.toString(),
					if (anonymous.value != true) loginData.user else null,
					if (anonymous.value != true) appSharedSecret else null,
					anonymous.value == true,
					userDataResponse.masterData.timeGrid
						?: TimeGrid.generateDefault(),
					userDataResponse.masterData.timeStamp,
					userDataResponse.userData,
					userDataResponse.settings,
					bookmarks = bookmarks
				)

				userDatabase.userDao().let { dao ->
					if (existingUserId == null)
						userId = dao.insert(user)
					else
						dao.update(user)

					dao.deleteUserData(userId)
					dao.insertUserData(
						userId,
						userDataResponse.masterData
					)
				}

				if (advanced && !proxyUrl.value.isNullOrEmpty())
					proxyHostPref.saveValue(proxyUrl.value)

				setResult(Activity.RESULT_OK)
				finish()
			}
		}*/
	}

	fun onQrCodeErrorDialogDismiss() {
		showQrCodeErrorDialog = false
	}
}

// Using `null` as the default values allows for partial data updates
class LoginData(
	initialProfileName: String? = null,
	initialSchoolId: String? = null,
	initialAnonymous: Boolean? = null,
	initialUsername: String? = null,
	initialApiUrl: String? = null,
) {
	val profileName = mutableStateOf(initialProfileName)
	val schoolId = mutableStateOf(initialSchoolId)
	val anonymous = mutableStateOf(initialAnonymous)
	val username = mutableStateOf(initialUsername)
	val password = mutableStateOf<String?>(null)
	val proxyUrl = mutableStateOf<String?>(null)
	val apiUrl = mutableStateOf(initialApiUrl)
	val skipAppSecret = mutableStateOf<Boolean?>(null)
}
