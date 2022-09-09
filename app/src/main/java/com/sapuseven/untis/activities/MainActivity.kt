package com.sapuseven.untis.activities

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.RectF
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.View
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.ColorUtils
import com.sapuseven.untis.R
import com.sapuseven.untis.activities.SettingsActivity.Companion.EXTRA_STRING_PREFERENCE_HIGHLIGHT
import com.sapuseven.untis.activities.SettingsActivity.Companion.EXTRA_STRING_PREFERENCE_ROUTE
import com.sapuseven.untis.activities.main.DrawerItems
import com.sapuseven.untis.activities.main.DrawerText
import com.sapuseven.untis.adapters.ProfileListAdapter
import com.sapuseven.untis.data.databases.UserDatabase
import com.sapuseven.untis.data.timetable.TimegridItem
import com.sapuseven.untis.helpers.ConversionUtils
import com.sapuseven.untis.helpers.DateTimeUtils
import com.sapuseven.untis.helpers.config.PreferenceHelper
import com.sapuseven.untis.helpers.timetable.TimetableDatabaseInterface
import com.sapuseven.untis.helpers.timetable.TimetableLoader
import com.sapuseven.untis.models.untis.UntisDate
import com.sapuseven.untis.models.untis.timetable.PeriodElement
import com.sapuseven.untis.preferences.preference.decodeStoredTimetableValue
import com.sapuseven.untis.ui.common.ElementPickerDialogFullscreen
import com.sapuseven.untis.ui.common.ProfileSelectorAction
import com.sapuseven.untis.ui.common.Weekday
import com.sapuseven.untis.ui.common.disabled
import com.sapuseven.untis.ui.dialogs.ProfileManagementDialog
import com.sapuseven.untis.ui.dialogs.TimetableItemDetailsDialog
import com.sapuseven.untis.ui.theme.AppTheme
import com.sapuseven.untis.viewmodels.PeriodDataViewModel
import com.sapuseven.untis.views.WeekViewSwipeRefreshLayout
import com.sapuseven.untis.views.weekview.WeekView
import com.sapuseven.untis.views.weekview.WeekViewDisplayable
import com.sapuseven.untis.views.weekview.WeekViewEvent
import com.sapuseven.untis.views.weekview.listeners.EventClickListener
import com.sapuseven.untis.views.weekview.listeners.ScaleListener
import com.sapuseven.untis.views.weekview.listeners.ScrollListener
import com.sapuseven.untis.views.weekview.loaders.WeekViewLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.joda.time.DateTime
import org.joda.time.DateTimeConstants
import org.joda.time.Instant
import org.joda.time.LocalDate
import org.joda.time.format.DateTimeFormat
import java.lang.ref.WeakReference
import java.util.*

class MainActivity :
	BaseComposeActivity()/*,
		NavigationView.OnNavigationItemSelectedListener,
		WeekViewLoader.PeriodChangeListener<TimegridItem>,
		EventClickListener<TimegridItem>,
		TopLeftCornerClickListener,
		TimetableDisplay,
		TimetableItemDetailsFragment.TimetableItemDetailsDialogListener*/ {

	companion object {
		private const val MINUTE_MILLIS: Int = 60 * 1000
		private const val HOUR_MILLIS: Int = 60 * MINUTE_MILLIS
		private const val DAY_MILLIS: Int = 24 * HOUR_MILLIS

		private const val REQUEST_CODE_LOGINDATAINPUT_ADD = 3
		private const val REQUEST_CODE_LOGINDATAINPUT_EDIT = 4
		private const val REQUEST_CODE_ERRORS = 5

		private const val UNTIS_DEFAULT_COLOR = "#f49f25"

		private const val PERSISTENT_INT_ZOOM_LEVEL = "persistent_zoom_level"

		private const val MESSENGER_PACKAGE_NAME = "com.untis.chat"

		private const val FRAGMENT_TAG_LESSON_INFO = "com.sapuseven.untis.fragments.lessoninfo"
		private const val FRAGMENT_TAG_ABSENCE_CHECK = "com.sapuseven.untis.fragments.absencecheck"
	}

	private var lastBackPress: Long = 0
	//private var profileId: Long = -1

	private var lastPickedDate: DateTime? = null
	private var proxyHost: String? = null
	private var profileUpdateDialog: AlertDialog? = null
	private val weekViewRefreshHandler = Handler(Looper.getMainLooper())
	private lateinit var profileListAdapter: ProfileListAdapter
	private var BOOKMARKS_ADD_ID: Int = 0
	private val timetableItemDetailsViewModel: PeriodDataViewModel by viewModels()

	// TODO
	/*private val weekViewUpdate = object : Runnable {
		override fun run() {
			weekView.invalidate()
			weekViewRefreshHandler.postDelayed(this, 60 * 1000)
		}
	}*/

	private val shortcutLauncher =
		registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
			// TODO: Look at it.data for potential actions (e.g. show a specific timetable)
		}

	private val settingsLauncher =
		registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
			// TODO: Look at it.data for potential actions (e.g. show a specific timetable)
		}

	private val loginLauncher =
		registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
			// TODO: Look at it.data for potential actions (e.g. show a specific timetable)
		}

	@OptIn(ExperimentalMaterial3Api::class)
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		//setupNotifications()

		setContent {
			AppTheme {
				withUser (
					invalidContent = { login() }
				) { user ->
					/*weekView.setOnCornerClickListener(this)*/
					val appState = rememberMainAppState(user, timetableDatabaseInterface)

					Drawer(
						appState = appState,
						onShowTimetable = {
							it.let { element ->
								appState.displayElement(element?.first, element?.second)
							}
						}
					) {
						Scaffold(
							topBar = {
								CenterAlignedTopAppBar(
									title = { Text(appState.displayedName.value) },
									navigationIcon = {
										IconButton(onClick = {
											appState.coroutineScope.launch { appState.drawerState.open() }
										}) {
											Icon(
												imageVector = Icons.Outlined.Menu,
												contentDescription = stringResource(id = R.string.main_drawer_open)
											)
										}
									},
									actions = {
										ProfileSelectorAction(
											users = userDatabase.getAllUsers(),
											currentSelectionId = user.id,
											showProfileActions = true,
											onSelectionChange = {
												loadUser(it)
											},
											onActionEdit = {
												appState.profileManagementDialog.value = true
											}
										)
									}
								)
							},
						) { innerPadding ->
							Box(
								modifier = Modifier
									.padding(innerPadding)
									.fillMaxSize()
							) {
								WeekViewCompose(appState)

								Text(
									text = appState.lastRefreshText(),
									modifier = Modifier
										.align(Alignment.BottomStart)
										.padding(start = 48.dp, bottom = 8.dp)
										.disabled(appState.isAnonymous)
								)

								if (appState.isAnonymous) {
									Column(
										verticalArrangement = Arrangement.Center,
										horizontalAlignment = Alignment.CenterHorizontally,
										modifier = Modifier
											.fillMaxSize()
											.absolutePadding(left = 16.dp)
									) {
										Text(
											text = stringResource(id = R.string.main_anonymous_login_info_text),
											textAlign = TextAlign.Center,
											modifier = Modifier
												.padding(horizontal = 32.dp)
										)

										Button(
											onClick = {
												settingsLauncher.launch(
													Intent(
														this@MainActivity,
														SettingsActivity::class.java
													).apply {
														putExtra(EXTRA_LONG_PROFILE_ID, user.id)
														putExtra(
															EXTRA_STRING_PREFERENCE_ROUTE,
															"preferences_timetable"
														)
														putExtra(
															EXTRA_STRING_PREFERENCE_HIGHLIGHT,
															"preference_timetable_personal_timetable"
														)
													}
												)
											},
											modifier = Modifier
												.padding(top = 16.dp)
										) {
											Text(text = stringResource(id = R.string.main_go_to_settings))
										}
									}
								}

								if (appState.isLoading)
									CircularProgressIndicator(
										modifier = Modifier
											.align(Alignment.BottomEnd)
											.padding(8.dp)
									)

								/*viewModelStore.clear() // TODO: Doesn't seem like the best solution. This could potentially interfere with other ViewModels scoped to this activity.
								val fragment = TimetableItemDetailsFragment(data, timetableDatabaseInterface, profileUser)

								supportFragmentManager.beginTransaction().run {
									setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
									add(R.id.content_main, fragment, FRAGMENT_TAG_LESSON_INFO)
									addToBackStack(fragment.tag)
									commit()
								}*/
							}
						}
					}

					val density = LocalDensity.current
					val offsetY = { _: Int ->
						with(density) { 40.dp.roundToPx() }
					}
					// TODO: Implement a smoother animation (see https://m3.material.io/components/dialogs/guidelines#007536b9-76b1-474a-a152-2f340caaff6f)
					AnimatedVisibility(
						visible = appState.timetableItemDetailsDialog.value != null,
						enter = slideInVertically(initialOffsetY = offsetY) + fadeIn(),
						exit = slideOutVertically(targetOffsetY = offsetY) + fadeOut()
					) {
						TimetableItemDetailsDialog(
							timegridItem = remember { appState.timetableItemDetailsDialog.value!! },
							timetableDatabaseInterface = timetableDatabaseInterface,
							onDismiss = {
								appState.timetableItemDetailsDialog.value = null
								it?.let { appState.displayElement(it) }
							}
						)
					}
					AnimatedVisibility(
						visible = appState.profileManagementDialog.value,
						enter = slideInVertically(initialOffsetY = offsetY) + fadeIn(),
						exit = slideOutVertically(targetOffsetY = offsetY) + fadeOut()
					) {
						ProfileManagementDialog(
							onDismiss = {
								appState.profileManagementDialog.value = false
							}
						)
					}
				}
			}
		}

		/*if (checkForCrashes()) {
			startActivityForResult(Intent(this, ErrorsActivity::class.java).apply {
				putExtra(ErrorsActivity.EXTRA_BOOLEAN_SHOW_CRASH_MESSAGE, true)
			}, REQUEST_CODE_ERRORS)
		} else {
			setupActionBar()
			setupNavDrawer()

			setupViews()
			setupHours()
			setupHolidays()

			setupTimetableLoader()
			if (!checkShortcut()) showPersonalTimetable()
			refreshNavigationViewSelection()
		}*/
	}

	@Composable
	private fun WeekViewCompose(appState: MainAppState) {
		var weekViewGlobal by remember { mutableStateOf(appState.weekView.value) }

		AndroidView(
			factory = { context ->
				if (weekViewGlobal == null) { // Create weekView if it doesn't already exist
					WeekView<TimegridItem>(context).also {
						weekViewGlobal = it
						appState.weekView.value = it
					}
				}

				appState.weekViewSwipeRefresh.value ?:
					WeekViewSwipeRefreshLayout(context).apply {
						appState.weekViewSwipeRefresh.value = this
						addView(weekViewGlobal)
					}
			},
			update = {
				appState.weekView.value = weekViewGlobal
				appState.updateViews(it)
			},
			modifier = Modifier
				.fillMaxSize()
				.disabled(appState.isAnonymous)
		)
	}

	@OptIn(ExperimentalMaterial3Api::class)
	@Composable
	private fun Drawer(
		appState: MainAppState,
		onShowTimetable: (Pair<PeriodElement?, String?>?) -> Unit,
		content: @Composable () -> Unit
	) {
		var showElementPicker by remember {
			mutableStateOf<TimetableDatabaseInterface.Type?>(
				null
			)
		}

		showElementPicker?.let { type ->
			ElementPickerDialogFullscreen(
				title = { /*TODO*/ },
				timetableDatabaseInterface = timetableDatabaseInterface,
				onDismiss = { showElementPicker = null },
				onSelect = { item ->
					item?.let {
						onShowTimetable(
							item to timetableDatabaseInterface.getLongName(it)
						)
					} ?: run {
						onShowTimetable(appState.personalTimetable)
					}
				},
				initialType = type
			)
		} ?: ModalNavigationDrawer(
			gesturesEnabled = appState.drawerGesturesEnabled,
			drawerState = appState.drawerState,
			drawerContent = {
				/*Text(
					text = profileUser.getDisplayedName(applicationContext)
						.ifBlank { stringResource(R.string.app_name) },
					modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
				)
				Text(
					text = profileUser.userData.schoolName.ifBlank { stringResource(R.string.all_contact_email) },
					modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
				)*/

				Spacer(modifier = Modifier.height(24.dp))

				DrawerText("Favourites")

				NavigationDrawerItem(
					icon = {
						Icon(
							painterResource(id = R.drawable.all_prefs_personal),
							contentDescription = null
						)
					},
					label = { Text(stringResource(id = R.string.all_personal_timetable)) },
					selected = appState.isPersonalTimetable,
					onClick = {
						appState.closeDrawer()
						onShowTimetable(appState.personalTimetable)
					},
					modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
				)

				NavigationDrawerItem(
					icon = {
						Icon(
							painterResource(id = R.drawable.all_add),
							contentDescription = null
						)
					},
					label = { Text(stringResource(id = R.string.maindrawer_bookmarks_add)) },
					selected = false,
					onClick = {
						appState.closeDrawer()
						//selectedItem.value = item
					},
					modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
				)

				DrawerText("Timetables")

				DrawerItems(
					isPersonalTimetableSelected = appState.isPersonalTimetable,
					displayedElement = appState.displayedElement.value,
					onTimetableClick = { item ->
						appState.closeDrawer()
						showElementPicker = item.elementType
					},
					onShortcutClick = { item ->
						appState.closeDrawer()

						shortcutLauncher.launch(
							Intent(
								this@MainActivity,
								item.target
							).apply {
								putExtra(EXTRA_LONG_PROFILE_ID, currentUserId())
							}
						)
					}
				)
			},
			content = content
		)
	}

/*private fun checkForProfileUpdateRequired(): Boolean {
	return profileUser.schoolId.isBlank() || profileUser.apiUrl.isBlank()
}

private fun checkForNewSchoolYear(): Boolean {
	val currentSchoolYearId = getCurrentSchoolYear()?.id ?: -1

	if (!preferences.has("school_year"))
	preferences["school_year"] = currentSchoolYearId

	return if (preferences["school_year", -1] != currentSchoolYearId) {
		preferences.has("school_year")
	} else false
}

private fun getCurrentSchoolYear(): SchoolYear? {
	val schoolYears = userDatabase.getAdditionalUserData<SchoolYear>(
		profileUser.id
			?: -1, SchoolYear()
	)?.values?.toList() ?: emptyList()

	return schoolYears.find {
		val now = LocalDate.now()
		now.isAfter(LocalDate(it.startDate)) && now.isBefore(LocalDate(it.endDate))
	}
}

override fun onPause() {
	weekViewRefreshHandler.removeCallbacks(weekViewUpdate)
	super.onPause()
}

override fun onResume() {
	super.onResume()
	if (timetableLoader == null) return

	refreshMessages(profileUser, navigationview_main)

	if (::weekView.isInitialized) {
		proxyHost = preferences["preference_connectivity_proxy_host", null]
		setupWeekViewConfig()

		weekViewRefreshHandler.post(weekViewUpdate)
	}
}

override fun onErrorLogFound() {
	// TODO: Extract string resources
	if (preferences["preference_additional_error_messages"])
		Snackbar.make(content_main, "Some errors have been found.", Snackbar.LENGTH_INDEFINITE)
			.setAction("Show") {
				startActivity(Intent(this, ErrorsActivity::class.java))
			}
			.show()
}

private fun showProfileUpdateRequired() {
	profileUpdateDialog = MaterialAlertDialogBuilder(this)
		.setTitle(getString(R.string.main_dialog_update_profile_title))
		.setMessage(getString(R.string.main_dialog_update_profile_message))
		.setPositiveButton(getString(R.string.main_dialog_update_profile_button)) { _, _ ->
			updateProfile(profileUser)
		}
		.setCancelable(false)
		.show()
}*/

	private fun login() {
		loginLauncher.launch(Intent(this, LoginActivity::class.java))
		finish()
	}

/*
private fun checkShortcut(): Boolean {
	return intent.extras?.let { extras ->
		val userId = extras.getLong("user")
		if (preferences.loadProfileId() != userId)
			switchToProfile(userDatabase.getUser(userId) ?: return false)

		val element = PeriodElement(
			type = extras.getString("type") ?: return false,
			id = extras.getInt("id"),
			orgId = extras.getInt("orgId")
		)
		val useOrgId = extras.getBoolean("useOrgId")
		setTarget(
			if (useOrgId) element.orgId else element.id,
			element.type,
			timetableDatabaseInterface.getLongName(
				if (useOrgId) element.orgId else element.id,
				TimetableDatabaseInterface.Type.valueOf(element.type)
			)
		)
		true
	} ?: false
}

private fun setupNotifications() {
	val intent = Intent(this, StartupReceiver::class.java)
	intent.putExtra(EXTRA_BOOLEAN_MANUAL, true)
	sendBroadcast(intent)
}

private fun setupTimetableLoader() {
	timetableLoader =
		TimetableLoader(WeakReference(this), this, profileUser, timetableDatabaseInterface)
}

private fun setupNavDrawer() {
	navigationview_main.setNavigationItemSelectedListener(this)
	navigationview_main.setCheckedItem(R.id.nav_show_personal)

	updateNavDrawer(navigationview_main)

	val header = navigationview_main.getHeaderView(0)
	val dropdown =
		header.findViewById<ConstraintLayout>(R.id.constraintlayout_mainactivitydrawer_dropdown)
	val dropdownView =
		header.findViewById<LinearLayout>(R.id.linearlayout_mainactivitydrawer_dropdown_view)
	val dropdownImage =
		header.findViewById<ImageView>(R.id.imageview_mainactivitydrawer_dropdown_arrow)
	val dropdownList =
		header.findViewById<RecyclerView>(R.id.recyclerview_mainactivitydrawer_profile_list)

	profileListAdapter = ProfileListAdapter(
		this,
		userDatabase.getAllUsers().toMutableList(),
		{ view ->
			toggleProfileDropdown(dropdownView, dropdownImage, dropdownList)
			switchToProfile(profileListAdapter.itemAt(dropdownList.getChildLayoutPosition(view)))
		},
		{ view ->
			closeDrawer()
			editProfile(profileListAdapter.itemAt(dropdownList.getChildLayoutPosition(view)))
			true
		})
	dropdownList.adapter = profileListAdapter
	dropdown.setOnClickListener {
		toggleProfileDropdown(dropdownView, dropdownImage, dropdownList)
	}

	val profileListAdd =
		header.findViewById<ConstraintLayout>(R.id.constraintlayout_mainactivitydrawer_add)
	profileListAdd.setOnClickListener {
		closeDrawer()
		addProfile()
	}
}

private fun updateNavDrawer(navigationView: NavigationView) {
	val line1 = profileUser.getDisplayedName(applicationContext)
	val line2 = profileUser.userData.schoolName
	(navigationView.getHeaderView(0)
		.findViewById<View>(R.id.textview_mainactivtydrawer_line1) as TextView).text =
			line1.ifBlank { getString(R.string.app_name) }
	(navigationView.getHeaderView(0)
		.findViewById<View>(R.id.textview_mainactivitydrawer_line2) as TextView).text =
			line2.ifBlank { getString(R.string.all_contact_email) }

	navigationView.menu.findItem(R.id.nav_messenger).isVisible = false
}

private fun toggleProfileDropdown(
	dropdownView: ViewGroup,
	dropdownImage: ImageView,
	dropdownList: RecyclerView
) {
	if (dropdownImage.scaleY < 0) {
		dropdownImage.scaleY = 1F
		dropdownView.visibility = View.GONE
	} else {
		dropdownImage.scaleY = -1F

		dropdownList.setHasFixedSize(true)
		dropdownList.layoutManager = LinearLayoutManager(this)

		dropdownView.visibility = View.VISIBLE
	}
}

private fun addProfile() {
	val loginIntent = Intent(this, LoginActivity::class.java)
	startActivityForResult(loginIntent, REQUEST_CODE_LOGINDATAINPUT_ADD)
}

private fun editProfile(user: UserDatabase.User) {
	val loginIntent = Intent(this, LoginDataInputActivity::class.java)
		.putExtra(LoginDataInputActivity.EXTRA_LONG_PROFILE_ID, user.id)
	startActivityForResult(loginIntent, REQUEST_CODE_LOGINDATAINPUT_EDIT)
}

private fun updateProfile(user: UserDatabase.User) {
	val loginIntent = Intent(this, LoginDataInputActivity::class.java)
		.putExtra(LoginDataInputActivity.EXTRA_LONG_PROFILE_ID, user.id)
		.putExtra(EXTRA_BOOLEAN_PROFILE_UPDATE, true)
	startActivityForResult(loginIntent, REQUEST_CODE_LOGINDATAINPUT_EDIT)
}

private fun switchToProfile(user: UserDatabase.User) {
	profileId = user.id!!
	preferences.saveProfileId(profileId)
	if (loadProfile()) {
		updateNavDrawer(findViewById(R.id.navigationview_main))

		closeDrawer()
		setupTimetableLoader()
		showPersonalTimetable()
		refreshNavigationViewSelection()

		recreate()
	} else {
		timetableLoader = null
	}
}

private fun refreshMessages(user: UserDatabase.User, navigationView: NavigationView) =
	GlobalScope.launch(Dispatchers.Main) {
		loadMessages(user)?.let {
			navigationView.menu.findItem(R.id.nav_infocenter).icon = if (
				it.size > preferences["preference_last_messages_count", 0] ||
				(SimpleDateFormat(
					"dd-MM-yyyy",
					Locale.US
				).format(Calendar.getInstance().time) != preferences["preference_last_messages_date", ""] && it.isNotEmpty())
			) {
				getDrawable(R.drawable.all_infocenter_dot)
			} else {
				getDrawable(R.drawable.all_infocenter)
			}
		}
	}

//TODO: Duplicated function from info center
private suspend fun loadMessages(user: UserDatabase.User): List<UntisMessage>? {

	val query = UntisRequest.UntisRequestQuery(user)

	query.data.method = UntisApiConstants.METHOD_GET_MESSAGES
	query.proxyHost =
		preferences["preference_connectivity_proxy_host", null]
	query.data.params = listOf(
		MessageParams(
			UntisDate.fromLocalDate(LocalDate.now()),
			auth = UntisAuthentication.createAuthObject(user)
		)
	)

	val result = UntisRequest().request(query)
	return result.fold({ data ->
		val untisResponse = SerializationUtils.getJSON().decodeFromString<MessageResponse>(data)

		untisResponse.result?.messages
	}, { null })
}

private fun setupViews() {
	setupWeekView()
	restoreZoomLevel()

	textview_main_lastrefresh?.text =
		getString(R.string.main_last_refreshed, getString(R.string.main_last_refreshed_never))

	button_main_settings.setOnClickListener {
		val intent = Intent(this@MainActivity, SettingsActivity::class.java)
		intent.putExtra(SettingsActivity.EXTRA_LONG_PROFILE_ID, profileId)
		// TODO: Find a way to jump directly to the personal timetable setting
		startActivityForResult(intent, REQUEST_CODE_SETTINGS)
	}

	setupSwipeRefresh()
}

private fun setupSwipeRefresh() {
	swiperefreshlayout_main_timetable.setOnRefreshListener {
		displayedElement?.let { element ->
			weeklyTimetableItems[currentWeekIndex]?.dateRange?.let { dateRange ->
				loadTimetable(
					TimetableLoader.TimetableLoaderTarget(
						dateRange.first,
						dateRange.second,
						element.id,
						element.type
					), true
				)
			}
		}
	}
}*/

/*private fun setupWeekView() {
	weekView = findViewById(R.id.weekview_main_timetable)
	weekView.setOnEventClickListener(this)
	weekView.setOnCornerClickListener(this)
	weekView.setPeriodChangeListener(this)
	weekView.scrollListener = object : ScrollListener {
		override fun onFirstVisibleDayChanged(
			newFirstVisibleDay: LocalDate,
			oldFirstVisibleDay: LocalDate?
		) {
			currentWeekIndex = convertDateTimeToWeekIndex(newFirstVisibleDay)
			setLastRefresh(
				weeklyTimetableItems[currentWeekIndex]?.lastUpdated
					?: 0
			)
		}
	}
	weekView.scaleListener = object : ScaleListener {
		override fun onScaleFinished() {
			saveZoomLevel()
		}
	}
	setupWeekViewConfig()
}

private fun saveZoomLevel() {
	preferences[PERSISTENT_INT_ZOOM_LEVEL] = weekView.hourHeight
}

private fun restoreZoomLevel() {
	weekView.hourHeight = preferences[PERSISTENT_INT_ZOOM_LEVEL, weekView.hourHeight]
}

private fun setupHolidays() {
	userDatabase.getAdditionalUserData<Holiday>(profileUser.id!!, Holiday())?.let { item ->
		weekView.addHolidays(item.map {
			HolidayChip(
				text = it.value.longName,
				startDate = it.value.startDate,
				endDate = it.value.endDate
			)
		})
	}
}

private fun setupActionBar() {
	setSupportActionBar(toolbar_main)
	val toggle = ActionBarDrawerToggle(
		this,
		drawer_layout,
		toolbar_main,
		R.string.main_drawer_open,
		R.string.main_drawer_close
	)
	drawer_layout.addDrawerListener(toggle)
	toolbar_main.setNavigationOnClickListener { setBookmarksLongClickListeners() ; openDrawer() }
	toggle.syncState()
	window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
	window.statusBarColor = Color.TRANSPARENT
	supportFragmentManager.addOnBackStackChangedListener {
		if (supportFragmentManager.backStackEntryCount > 0) {
			toggle.isDrawerIndicatorEnabled = false
			supportActionBar?.setDisplayHomeAsUpEnabled(true)
			drawer_layout.setDrawerLockMode(
				DrawerLayout.LOCK_MODE_LOCKED_CLOSED,
				GravityCompat.START
			)
			toolbar_main.setNavigationOnClickListener { onBackPressed() }
			// TODO: Set actionBar title to match fragment
		} else {
			supportActionBar?.setDisplayHomeAsUpEnabled(false)
			toggle.isDrawerIndicatorEnabled = true
			toggle.syncState()
			drawer_layout.setDrawerLockMode(
				DrawerLayout.LOCK_MODE_UNLOCKED,
				GravityCompat.START
			)
			toolbar_main.setNavigationOnClickListener {  setBookmarksLongClickListeners() ; openDrawer() }
			// TODO: Set actionBar title to default
		}
	}
}

override fun onPrepareOptionsMenu(menu: Menu): Boolean {
	var i = 0
	navigationview_main.menu.findItem(R.id.nav_personal_bookmarks_title).subMenu?.let {
		// remove everything except personal timetable (in case menu has been invalidated)
		for (index in 0 until it.size()) {
			it.removeItem(index)
		}
		userDatabase.getUser(profileId)?.bookmarks?.forEach { bookmark ->
			it.add(0, i, Menu.FIRST + i, bookmark.displayName)
				.setIcon(bookmark.drawableId).isCheckable = true
			++i
		}
		BOOKMARKS_ADD_ID = i
		it.add(0, BOOKMARKS_ADD_ID, Menu.FIRST + i, getString(R.string.maindrawer_bookmarks_add)).setIcon(getDrawable(R.drawable.all_add))
		refreshNavigationViewSelection()
	}
	return super.onPrepareOptionsMenu(menu)
}*/

/*override fun onNavigationItemSelected(item: MenuItem): Boolean {
	when (item.itemId) {
		R.id.nav_show_personal -> {
			showPersonalTimetable()
			refreshNavigationViewSelection()
		}
		BOOKMARKS_ADD_ID -> {
			ElementPickerDialog.newInstance(
					timetableDatabaseInterface,
					ElementPickerDialog.Companion.ElementPickerDialogConfig(TimetableDatabaseInterface.Type.CLASS),
					object: ElementPickerDialog.ElementPickerDialogListener {
						override fun onDialogDismissed(dialog: DialogInterface?) { /* ignore */ }

						override fun onPeriodElementClick(fragment: Fragment, element: PeriodElement?, useOrgId: Boolean) {
							if(fragment is DialogFragment)
								fragment.dismiss()
							else
								removeFragment(fragment)
							val user = userDatabase.getUser(profileId)
							if(user != null) {
								element?.let {
									user.bookmarks = user.bookmarks.plus(TimetableBookmark(it.id, it.type, timetableDatabaseInterface.getShortName(it.id,
											TimetableDatabaseInterface.Type.valueOf(it.type)),
											when(TimetableDatabaseInterface.Type.valueOf(it.type)) {
												TimetableDatabaseInterface.Type.CLASS -> R.drawable.all_classes
												TimetableDatabaseInterface.Type.ROOM -> R.drawable.all_rooms
												TimetableDatabaseInterface.Type.TEACHER -> R.drawable.all_teacher
												TimetableDatabaseInterface.Type.SUBJECT -> R.drawable.all_subject }))
									userDatabase.editUser(user)
									updateNavDrawer(findViewById(R.id.navigationview_main))
									selectedElement = user.bookmarks.size - 1
									invalidateOptionsMenu()
									setTarget(it.id,it.type,timetableDatabaseInterface.getLongName(it.id, TimetableDatabaseInterface.Type.valueOf(it.type)))
								}
							}
						}

						override fun onPositiveButtonClicked(dialog: ElementPickerDialog) { /* not used */ }

					}

			).show(supportFragmentManager, "elementPicker")
		}
		R.id.nav_show_classes -> {
			showItemList(TimetableDatabaseInterface.Type.CLASS)
		}
		R.id.nav_show_teachers -> {
			showItemList(TimetableDatabaseInterface.Type.TEACHER)
		}
		R.id.nav_show_rooms -> {
			showItemList(TimetableDatabaseInterface.Type.ROOM)
		}
		R.id.nav_settings -> {
			val i = Intent(this@MainActivity, SettingsActivity::class.java)
			i.putExtra(SettingsActivity.EXTRA_LONG_PROFILE_ID, profileId)
			startActivityForResult(i, REQUEST_CODE_SETTINGS)
		}
		R.id.nav_infocenter -> {
			val i = Intent(this@MainActivity, InfoCenterActivity::class.java)
			i.putExtra(InfoCenterActivity.EXTRA_LONG_PROFILE_ID, profileId)
			startActivityForResult(i, REQUEST_CODE_ROOM_FINDER)
		}
		R.id.nav_messenger -> {
			try {
				startActivity(packageManager.getLaunchIntentForPackage(MESSENGER_PACKAGE_NAME))
			} catch (e: Exception) {
				try {
					startActivity(
						Intent(
							Intent.ACTION_VIEW,
							Uri.parse("market://details?id=$MESSENGER_PACKAGE_NAME")
						)
					)
				} catch (e: Exception) {
					startActivity(
						Intent(
							Intent.ACTION_VIEW,
							Uri.parse("https://play.google.com/store/apps/details?id=$MESSENGER_PACKAGE_NAME")
						)
					)
				}
			}
		}
		R.id.nav_free_rooms -> {
			val i = Intent(this@MainActivity, RoomFinderActivity::class.java)
			i.putExtra(RoomFinderActivity.EXTRA_LONG_PROFILE_ID, profileId)
			startActivityForResult(i, REQUEST_CODE_ROOM_FINDER)
		}
		else -> {
			val bookmarks = userDatabase.getUser(profileId)?.bookmarks
			if (bookmarks != null) {
				if(item.itemId < bookmarks.size){
					val target = bookmarks[item.itemId]
					selectedElement = item.itemId
					setTarget(target.classId,target.type,timetableDatabaseInterface.getLongName(target.classId, TimetableDatabaseInterface.Type.valueOf(target.type)))
				}
			}
		}
	}
	closeDrawer()
	return true
}

private fun showItemList(type: TimetableDatabaseInterface.Type) {
	ElementPickerDialog.newInstance(
			timetableDatabaseInterface,
			ElementPickerDialog.Companion.ElementPickerDialogConfig(type),
			object: ElementPickerDialog.ElementPickerDialogListener {
				override fun onDialogDismissed(dialog: DialogInterface?) { refreshNavigationViewSelection() }

				override fun onPeriodElementClick(fragment: Fragment, element: PeriodElement?, useOrgId: Boolean) {
					if (fragment is DialogFragment)
						fragment.dismiss()
					else
						removeFragment(fragment)
					element?.let {
						setTarget(if (useOrgId) element.orgId else element.id, element.type, timetableDatabaseInterface.getLongName(
								if (useOrgId) element.orgId else element.id, TimetableDatabaseInterface.Type.valueOf(element.type)))
					} ?: run {
						showPersonalTimetable()
					}
					selectedElement = when (element?.type) {
						TimetableDatabaseInterface.Type.CLASS.name -> R.id.nav_show_classes
						TimetableDatabaseInterface.Type.TEACHER.name -> R.id.nav_show_teachers
						TimetableDatabaseInterface.Type.ROOM.name -> R.id.nav_show_rooms
						else -> {R.id.nav_show_personal}
					}
					refreshNavigationViewSelection()
				}

				override fun onPositiveButtonClicked(dialog: ElementPickerDialog) { /* not used */ }

			}
	).show(supportFragmentManager, "elementPicker") // TODO: Do not hard-code the tag
}

override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
	super.onActivityResult(requestCode, resultCode, intent)

	when (requestCode) {
		REQUEST_CODE_ROOM_FINDER ->
			if (resultCode == Activity.RESULT_OK)
				(data?.getIntExtra(RoomFinderActivity.EXTRA_INT_ROOM_ID, -1)
					?: -1).let { roomId ->
					if (roomId != -1)
						@Suppress("RemoveRedundantQualifierName")
						setTarget(
							roomId,
							TimetableDatabaseInterface.Type.ROOM.toString(),
							timetableDatabaseInterface.getLongName(
								roomId,
								TimetableDatabaseInterface.Type.ROOM
							)
						)
				}
		REQUEST_CODE_SETTINGS -> recreate()
		REQUEST_CODE_LOGINDATAINPUT_ADD ->
			if (resultCode == Activity.RESULT_OK)
				recreate()
		REQUEST_CODE_LOGINDATAINPUT_EDIT ->
			if (resultCode == Activity.RESULT_OK) {
				recreate()
			} else if (checkForNewSchoolYear()) {
				finish()
			}
		REQUEST_CODE_ERRORS -> recreate()
	}
}

override fun onBackPressed() {
	if (drawer_layout.isDrawerOpen(GravityCompat.START)) {
		closeDrawer(drawer_layout)
	} else if (supportFragmentManager.backStackEntryCount > 0) {
		super.onBackPressed()
	} else if (!showPersonalTimetable()) {
		if (System.currentTimeMillis() - 2000 > lastBackPress && preferences["preference_double_tap_to_exit"]) {
			Snackbar.make(
				content_main,
				R.string.main_press_back_double, 2000
			).show()
			lastBackPress = System.currentTimeMillis()
		} else {
			super.onBackPressed()
		}
	} else {
		refreshNavigationViewSelection()
	}
}

private fun setTarget(anonymous: Boolean, displayName: CharSequence): Boolean {
	supportActionBar?.title = displayName
	if (anonymous) {
		showLoading(false)

		weeklyTimetableItems.clear()
		weekView.notifyDataSetChanged()

		constraintlayout_main_anonymouslogininfo.visibility = View.VISIBLE

		if (displayedElement == null) return false
		displayedElement = null
	} else {
		constraintlayout_main_anonymouslogininfo.visibility = View.GONE
	}
	return true
}

private fun setTarget(id: Int, type: String, displayName: String?): Boolean {
	displayNameCache = displayName ?: getString(R.string.app_name)
	PeriodElement(type, id, id).let {
		if (it == displayedElement) return false
		displayedElement = it
	}

	setTarget(false, displayNameCache)

	weeklyTimetableItems.clear()
	weekView.notifyDataSetChanged()
	return true
}

internal fun setFullscreenDialogActionBar() {
	supportActionBar?.setHomeAsUpIndicator(R.drawable.all_close)
	supportActionBar?.setTitle(R.string.all_lesson_details)
}

internal fun setDefaultActionBar() {
	supportActionBar?.title = displayNameCache
}

override fun onEventClick(data: TimegridItem, eventRect: RectF) {
	viewModelStore.clear() // TODO: Doesn't seem like the best solution. This could potentially interfere with other ViewModels scoped to this activity.
	val fragment = TimetableItemDetailsFragment(data, timetableDatabaseInterface, profileUser)

	supportFragmentManager.beginTransaction().run {
		setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
		add(R.id.content_main, fragment, FRAGMENT_TAG_LESSON_INFO)
		addToBackStack(fragment.tag)
		commit()
	}
}

override fun onPeriodElementClick(
	fragment: Fragment,
	element: PeriodElement?,
	useOrgId: Boolean
) {
	if (fragment is DialogFragment)
		fragment.dismiss()
	else
		removeFragment(fragment)
	element?.let {
		setTarget(
			if (useOrgId) element.orgId else element.id,
			element.type,
			timetableDatabaseInterface.getLongName(
				if (useOrgId) element.orgId else element.id,
				TimetableDatabaseInterface.Type.valueOf(element.type)
			)
		)
	} ?: run {
		showPersonalTimetable()
	}
	refreshNavigationViewSelection()
}

override fun onPeriodAbsencesClick() {
	val absenceEditFragment = AbsenceCheckFragment()

	supportFragmentManager.beginTransaction().run {
		setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
		add(R.id.content_main, absenceEditFragment, FRAGMENT_TAG_ABSENCE_CHECK)
		addToBackStack(absenceEditFragment.tag)
		commit()
	}
}

override fun onLessonTopicClick() {
	val dialogView = layoutInflater.inflate(R.layout.dialog_edit_lessontopic, null)
	val etLessonTopic = dialogView.findViewById<TextInputEditText>(R.id.edittext_dialog)

	etLessonTopic.setText(timetableItemDetailsViewModel.periodData().value?.topic?.text ?: "")

	MaterialAlertDialogBuilder(this)
		.setView(dialogView)
		.setPositiveButton(R.string.all_ok) { dialog, _ ->
			val lessonTopic = etLessonTopic.text.toString()
			timetableItemDetailsViewModel.submitLessonTopic(lessonTopic)
			dialog.dismiss()
		}
		.show()
}


private fun removeFragment(fragment: Fragment) {
	supportFragmentManager.popBackStack(fragment.tag, FragmentManager.POP_BACK_STACK_INCLUSIVE)
}

private fun refreshNavigationViewSelection() {
	selectedElement?.let { (navigationview_main as NavigationView).setCheckedItem(it) }
}

private fun setLastRefresh(timestamp: Long) {
	textview_main_lastrefresh?.text = if (timestamp > 0L)
		getString(
			R.string.main_last_refreshed,
			formatTimeDiff(Instant.now().millis - timestamp)
		)
	else
		getString(R.string.main_last_refreshed, getString(R.string.main_last_refreshed_never))
}*/

/*override fun addTimetableItems(
	items: List<TimegridItem>,
	startDate: UntisDate,
	endDate: UntisDate,
	timestamp: Long
) {
	for (item in items) {
		if (item.periodData.element.messengerChannel != null) {
			navigationview_main.menu.findItem(R.id.nav_messenger).isVisible = true
			break
		}
	}

	weeklyTimetableItems[convertDateTimeToWeekIndex(startDate.toLocalDate())]?.apply {
		this.items = prepareItems(items).map { it.toWeekViewEvent() }
		lastUpdated = timestamp
	}
	weekView.notifyDataSetChanged()

	// TODO: Only disable these loading indicators when everything finished loading
	showLoading(false)
}

override fun onTimetableLoadingError(requestId: Int, code: Int?, message: String?) {
	if (timetableLoader == null) return

	when (code) {
		TimetableLoader.CODE_CACHE_MISSING -> timetableLoader!!.repeat(
			requestId,
			TimetableLoader.FLAG_LOAD_SERVER,
			proxyHost
		)
		else -> {
			showLoading(false)
			Snackbar.make(
				content_main,
				if (code != null) ErrorMessageDictionary.getErrorMessage(
					resources,
					code
				) else message
					?: getString(R.string.all_error),
				Snackbar.LENGTH_INDEFINITE
			)
				.setAction("Show") {
					ErrorReportingDialog(this).showRequestErrorDialog(
						requestId,
						code,
						message
					)
				}
				.show()
		}
	}
}

private fun showLoading(loading: Boolean) {
	if (!loading) swiperefreshlayout_main_timetable.isRefreshing = false
	progressbar_main_loading?.visibility = if (loading) View.VISIBLE else View.GONE
}

override fun onCornerClick() {
	val fragment = DatePickerDialog()

	lastPickedDate?.let {
		val args = Bundle()
		args.putInt("year", it.year)
		args.putInt("month", it.monthOfYear)
		args.putInt("day", it.dayOfMonth)
		fragment.arguments = args
	}
	fragment.dateSetListener =
		android.app.DatePickerDialog.OnDateSetListener { _, year, month, dayOfMonth ->
			DateTime().withDate(year, month + 1, dayOfMonth).let {
				// +1 compensates for conversion from Calendar to DateTime
				weekView.goToDate(it)
				lastPickedDate = it
			}
		}
	fragment.show(supportFragmentManager, "datePicker")
}

override fun onCornerLongClick() = weekView.goToToday()

private fun openDrawer(drawer: DrawerLayout = drawer_layout) =
	drawer.openDrawer(GravityCompat.START)

private fun closeDrawer(drawer: DrawerLayout = drawer_layout) =
	drawer.closeDrawer(GravityCompat.START)*/

/*private fun setBookmarksLongClickListeners() {
	(navigationview_main[0] as RecyclerView).let { rv ->
		rv.post {
			for (index in 3..rv.layoutManager?.itemCount!!) {
				val bookmarks = userDatabase.getUser(profileId)?.bookmarks
				if(bookmarks != null){
					if(index-3 < bookmarks.size) {
						rv.layoutManager?.findViewByPosition(index)?.setOnLongClickListener {
							Log.d("Bookmark", "$index")
							closeDrawer()
							MaterialAlertDialogBuilder(this).setMessage(getString(R.string.main_dialog_delete_bookmark))
									.setPositiveButton(getString(R.string.all_yes)) { _, _ ->
										userDatabase.getUser(profileId)?.let { user ->
											if(selectedElement == index-3) {
												showPersonalTimetable()
											}
											user.bookmarks = user.bookmarks.minus(bookmarks[index-3])
											if(selectedElement!! < bookmarks.size && selectedElement!! > index-3){
												selectedElement = selectedElement!! - 1
											}
											userDatabase.editUser(user)
											invalidateOptionsMenu()
										}
									}
									.setNegativeButton(getString(R.string.all_no)) { _, _ -> }
									.show()
							true
						}
					}
				}

			}
		}
	}
}*/
}

class MainAppState @OptIn(ExperimentalMaterial3Api::class) constructor(
	val user: UserDatabase.User,
	val timetableDatabaseInterface: TimetableDatabaseInterface,
	val weekViewSwipeRefresh: MutableState<WeekViewSwipeRefreshLayout?>,
	val weekView: MutableState<WeekView<TimegridItem>?>,
	val context: Context,
	val coroutineScope: CoroutineScope,
	val colorScheme: ColorScheme,
	val currentDensity: Density,
	val preferences: PreferenceHelper, // TODO: Remove
	val personalTimetable: Pair<PeriodElement?, String?>?,
	val defaultDisplayedName: String,
	val drawerState: DrawerState,
	var drawerGestureState: MutableState<Boolean>,
	val loading: MutableState<Int>,
	val currentWeekIndex: MutableState<Int>,
	val lastRefreshTimestamp: MutableState<Long>,
	val weeklyTimetableItems: SnapshotStateMap<Int, WeeklyTimetableItems?>,
	val timetableLoader: TimetableLoader,
	val timetableItemDetailsDialog: MutableState<TimegridItem?>,
	val profileManagementDialog: MutableState<Boolean>
) {
	companion object {
		private const val MINUTE_MILLIS: Int = 60 * 1000
		private const val HOUR_MILLIS: Int = 60 * MINUTE_MILLIS
		private const val DAY_MILLIS: Int = 24 * HOUR_MILLIS

		private const val UNTIS_DEFAULT_COLOR = "#f49f25"
	}

	var drawerGestures by drawerGestureState

	var displayedElement: MutableState<PeriodElement?> = mutableStateOf(personalTimetable?.first)
	var displayedName: MutableState<String> =
		mutableStateOf(personalTimetable?.second ?: defaultDisplayedName)

	val isPersonalTimetable: Boolean
		get() = displayedElement.value == personalTimetable?.first

	val isAnonymous: Boolean
		get() = displayedElement.value == null

	val isLoading: Boolean
		get() = loading.value > 0

	var isRefreshing: Boolean by mutableStateOf(false)

	var shouldUpdateWeekView = true

	@OptIn(ExperimentalMaterial3Api::class)
	val drawerGesturesEnabled: Boolean
		get() = drawerGestures || drawerState.isOpen

	@OptIn(ExperimentalMaterial3Api::class)
	fun closeDrawer() {
		coroutineScope.launch { drawerState.close() }
	}

	fun displayElement(element: PeriodElement?, name: String? = null) {
		displayedElement.value = element
		displayedName.value = name ?: element?.let { timetableDatabaseInterface.getLongName(it) } ?: defaultDisplayedName

		weeklyTimetableItems.clear()
		weekView.value?.notifyDataSetChanged()
	}

	@Composable
	fun lastRefreshText() = stringResource(
		id = R.string.main_last_refreshed,
		if (lastRefreshTimestamp.value > 0L)
			formatTimeDiff(Instant.now().millis - lastRefreshTimestamp.value)
		else
			stringResource(id = R.string.main_last_refreshed_never)
	)

	@OptIn(ExperimentalComposeUiApi::class)
	@Composable
	private fun formatTimeDiff(diff: Long): String {
		return when {
			diff < MINUTE_MILLIS -> stringResource(R.string.main_time_diff_just_now)
			diff < HOUR_MILLIS -> pluralStringResource(
				R.plurals.main_time_diff_minutes,
				((diff / MINUTE_MILLIS).toInt()),
				diff / MINUTE_MILLIS
			)
			diff < DAY_MILLIS -> pluralStringResource(
				R.plurals.main_time_diff_hours,
				((diff / HOUR_MILLIS).toInt()),
				diff / HOUR_MILLIS
			)
			else -> pluralStringResource(
				R.plurals.main_time_diff_days,
				((diff / DAY_MILLIS).toInt()),
				diff / DAY_MILLIS
			)
		}
	}

	private fun Int.darken(ratio: Float) = ColorUtils.blendARGB(this, Color.BLACK, ratio)

	data class WeeklyTimetableItems(
		var items: List<WeekViewEvent<TimegridItem>> = emptyList(),
		var lastUpdated: Long = 0,
		var dateRange: Pair<UntisDate, UntisDate>? = null
	)

	private fun prepareItems(
		items: List<TimegridItem>,
		colorScheme: ColorScheme
	): List<TimegridItem> {
		val newItems = mergeItems(items.mapNotNull { item ->
			if (preferences["preference_timetable_hide_cancelled"] && item.periodData.isCancelled())
				return@mapNotNull null

			if (preferences["preference_timetable_substitutions_irregular"]) {
				item.periodData.apply {
					forceIrregular =
						classes.find { it.id != it.orgId } != null
								|| teachers.find { it.id != it.orgId } != null
								|| subjects.find { it.id != it.orgId } != null
								|| rooms.find { it.id != it.orgId } != null
								|| preferences["preference_timetable_background_irregular"]
								&& item.periodData.element.backColor != UNTIS_DEFAULT_COLOR
				}
			}
			item
		})
		colorItems(newItems, colorScheme)
		return newItems
	}

	private fun mergeItems(items: List<TimegridItem>): List<TimegridItem> {
		val days = user.timeGrid.days
		val itemGrid: Array<Array<MutableList<TimegridItem>>> =
			Array(days.size) { Array(days.maxByOrNull { it.units.size }!!.units.size) { mutableListOf() } }
		val leftover: MutableList<TimegridItem> = mutableListOf()

		// TODO: Check if the day from the Untis API is always an english string
		val firstDayOfWeek =
			DateTimeConstants.MONDAY //DateTimeFormat.forPattern("EEE").withLocale(Locale.ENGLISH).parseDateTime(days.first().day).dayOfWeek

		// Put all items into a two dimensional array depending on day and hour
		items.forEach { item ->
			val startDateTime = item.periodData.element.startDateTime.toLocalDateTime()
			val endDateTime = item.periodData.element.endDateTime.toLocalDateTime()

			val day = endDateTime.dayOfWeek - firstDayOfWeek

			if (day < 0 || day >= days.size) return@forEach

			val thisUnitStartIndex = days[day].units.indexOfFirst {
				it.startTime.time == startDateTime.toString(DateTimeUtils.tTimeNoSeconds())
			}

			val thisUnitEndIndex = days[day].units.indexOfFirst {
				it.endTime.time == endDateTime.toString(DateTimeUtils.tTimeNoSeconds())
			}

			if (thisUnitStartIndex != -1 && thisUnitEndIndex != -1)
				itemGrid[day][thisUnitStartIndex].add(item)
			else
				leftover.add(item)
		}

		val newItems = mutableListOf<TimegridItem>()
		newItems.addAll(leftover) // Add items that didn't fit inside the timegrid. These will always be single lessons.
		itemGrid.forEach { unitsOfDay ->
			unitsOfDay.forEachIndexed { unitIndex, items ->
				items.forEach {
					var i = 1
					while (unitIndex + i < unitsOfDay.size && it.mergeWith(unitsOfDay[unitIndex + i])) i++
				}

				newItems.addAll(items)
			}
		}
		return newItems
	}

	private fun colorItems(
		items: List<TimegridItem>,
		colorScheme: ColorScheme
	) {
		val regularColor = preferences.get<Int>("preference_background_regular")
		val examColor = preferences.get<Int>("preference_background_exam")
		val cancelledColor =
			preferences.get<Int>("preference_background_cancelled")
		val irregularColor =
			preferences.get<Int>("preference_background_irregular")

		val regularPastColor =
			preferences.get<Int>("preference_background_regular_past")
		val examPastColor =
			preferences.get<Int>("preference_background_exam_past")
		val cancelledPastColor =
			preferences.get<Int>("preference_background_cancelled_past")
		val irregularPastColor =
			preferences.get<Int>("preference_background_irregular_past")

		val useDefault =
			preferences.sharedPrefs!!.getStringSet("preference_school_background", emptySet())
				?: emptySet()
		val useTheme = true
		//if (!useDefault.contains("regular")) preferences["preference_use_theme_background"] else false

		items.forEach { item ->
			val defaultColor = Color.parseColor(item.periodData.element.backColor)

			item.color = when {
				item.periodData.isExam() -> if (useDefault.contains("exam")) defaultColor else colorScheme.error.toArgb()
				item.periodData.isCancelled() -> if (useDefault.contains("cancelled")) defaultColor else colorScheme.secondary.toArgb()
				item.periodData.isIrregular() -> if (useDefault.contains("irregular")) defaultColor else colorScheme.tertiary.toArgb()
				useTheme -> colorScheme.primary.toArgb()
				else -> if (useDefault.contains("regular")) defaultColor else regularColor
			}

			item.pastColor = when {
				item.periodData.isExam() -> if (useDefault.contains("exam")) defaultColor.darken(
					0.25f
				) else colorScheme.error.copy(alpha = .7f).toArgb()
				item.periodData.isCancelled() -> if (useDefault.contains("cancelled")) defaultColor.darken(
					0.25f
				) else colorScheme.secondary.copy(alpha = .7f).toArgb()
				item.periodData.isIrregular() -> if (useDefault.contains("irregular")) defaultColor.darken(
					0.25f
				) else colorScheme.tertiary.copy(alpha = .7f).toArgb()
				useTheme -> colorScheme.primary.copy(alpha = .7f).toArgb()/*if (currentTheme == "pixel") getAttr(R.attr.colorPrimary).darken(0.25f) else getAttr(
					R.attr.colorPrimaryDark
				)*/
				else -> if (useDefault.contains("regular")) defaultColor.darken(0.25f) else regularPastColor
			}

			item.textColor = when {
				item.periodData.isExam() -> if (useDefault.contains("exam")) defaultColor else colorScheme.onError.toArgb()
				item.periodData.isCancelled() -> if (useDefault.contains("cancelled")) defaultColor else colorScheme.onSecondary.toArgb()
				item.periodData.isIrregular() -> if (useDefault.contains("irregular")) defaultColor else colorScheme.onTertiary.toArgb()
				else -> colorScheme.onPrimary.toArgb()
			}
		}
	}

	private suspend fun loadTimetable(
		loader: TimetableLoader,
		target: TimetableLoader.TimetableLoaderTarget,
		forceRefresh: Boolean = false
	): Flow<Result<TimetableLoader.TimetableItems>> {
		val alwaysLoad: Boolean =
			preferences["preference_connectivity_refresh_in_background", false]
		val flags =
			(if (!forceRefresh) TimetableLoader.FLAG_LOAD_CACHE else 0) or (if (alwaysLoad || forceRefresh) TimetableLoader.FLAG_LOAD_SERVER else 0)
		return loader.loadAsync(target, flags, null/*preferences["preference_connectivity_proxy_host"]*/)
	}

	private suspend fun loadWeeklyTimetableItems(
		loader: TimetableLoader?,
		startDate: LocalDate,
		endDate: LocalDate,
		element: PeriodElement,
		colorScheme: ColorScheme,
		onItemsChanged: (items: WeeklyTimetableItems) -> Unit,
		forceRefresh: Boolean = false
	) {
		loader?.let {
			val dateRange =
				UntisDate.fromLocalDate(LocalDate(startDate)) to
						UntisDate.fromLocalDate(LocalDate(endDate))

			loadTimetable(
				loader,
				TimetableLoader.TimetableLoaderTarget(
					dateRange.first,
					dateRange.second,
					element.id,
					element.type
				), forceRefresh
			).collect { result ->
				result
					.onSuccess { timetableItems ->
						/*for (item in timetableItems.items) {
						if (item.periodData.element.messengerChannel != null) {
							navigationview_main.menu.findItem(R.id.nav_messenger).isVisible = true
							break
						}
					}*/
						onItemsChanged(
							WeeklyTimetableItems(
								dateRange = dateRange,
								items =
								prepareItems(
									timetableItems.items,
									colorScheme
								).map { item -> item.toWeekViewEvent() },
								lastUpdated = timetableItems.timestamp
							)
						)
					}
					.onFailure {
						when (it) {
							is TimetableLoader.TimetableLoaderException -> {
								Log.e(
									"MainActivity",
									it.untisErrorMessage ?: it.message ?: "unknown error"
								)

								when (it.untisErrorCode) {
									/*TimetableLoader.CODE_CACHE_MISSING -> timetableLoader!!.repeat(
										it.requestId,
										TimetableLoader.FLAG_LOAD_SERVER,
										proxyHost
									)*/
									else -> {
										/*Snackbar.make(
											content_main,
											if (code != null) ErrorMessageDictionary.getErrorMessage(
												resources,
												code
											) else message
												?: getString(R.string.all_error),
											Snackbar.LENGTH_INDEFINITE
										)
											.setAction("Show") {
												ErrorReportingDialog(this).showRequestErrorDialog(
													requestId,
													code,
													message
												)
											}
											.show()*/
									}
								}
							}
						}
					}
			}
		}
	}

	private fun convertDateTimeToWeekIndex(date: LocalDate) = date.year * 100 + date.dayOfYear / 7

	private fun onRefresh() {
		displayedElement.value?.let { element ->
			weeklyTimetableItems[currentWeekIndex.value]?.dateRange?.let { dateRange ->
				coroutineScope.launch {
					loadWeeklyTimetableItems(
						timetableLoader,
						dateRange.first.toLocalDate(),
						dateRange.second.toLocalDate(),
						element,
						colorScheme,
						forceRefresh = true,
						onItemsChanged = { items ->
							weeklyTimetableItems[currentWeekIndex.value] =
								items
							weekView.value?.notifyDataSetChanged()
						}
					)
					isRefreshing = false // TODO: When loading fails, this is never reached (why??)
				}
			}
		}
	}

	private fun <T> WeekView<T>.setupCustomization() {
			weekLength = preferences.sharedPrefs!!.getStringSet(
				"preference_week_custom_range",
				emptySet()
			)?.size?.zeroToNull ?: user.timeGrid.days.size
			numberOfVisibleDays =
				preferences["preference_week_custom_display_length", 0].zeroToNull
					?: weekLength
			firstDayOfWeek = preferences.sharedPrefs!!.getStringSet(
				"preference_week_custom_range",
				emptySet()
			)?.map { Weekday.valueOf(it) }
				?.minOrNull()?.ordinal ?: DateTimeFormat.forPattern("E")
				.withLocale(Locale.ENGLISH) // TODO: Correct locale?
				.parseDateTime(user.timeGrid.days[0].day).dayOfWeek
			timeColumnVisibility =
				!preferences.get<Boolean>("preference_timetable_hide_time_stamps")
			columnGap = ConversionUtils.dpToPx(
				preferences.get<Int>("preference_timetable_item_padding")
					.toFloat(), context
			).toInt()
			overlappingEventGap = ConversionUtils.dpToPx(
				preferences.get<Int>("preference_timetable_item_padding_overlap")
					.toFloat(), context
			).toInt()
			eventCornerRadius = ConversionUtils.dpToPx(
				preferences.get<Int>("preference_timetable_item_corner_radius")
					.toFloat(), context
			).toInt()
			eventSecondaryTextCentered =
				preferences["preference_timetable_centered_lesson_info"]
			eventTextBold =
				preferences["preference_timetable_bold_lesson_name"]
			eventTextSize = ConversionUtils.spToPx(
				preferences.get<Int>("preference_timetable_lesson_name_font_size")
					.toFloat(), context
			)
			eventSecondaryTextSize = ConversionUtils.spToPx(
				preferences.get<Int>("preference_timetable_lesson_info_font_size")
					.toFloat(), context
			)
			eventTextColor =
				if (preferences["preference_timetable_item_text_light"]) Color.WHITE else Color.BLACK
			pastBackgroundColor = preferences["preference_background_past"]
			futureBackgroundColor =
				preferences["preference_background_future"]
			nowLineColor = preferences["preference_marker"]
			horizontalFlingEnabled = preferences["preference_fling_enable"]
			snapToWeek =
				!preferences.get<Boolean>("preference_week_snap_to_days") && numberOfVisibleDays != 1
	}

	private fun <T> WeekView<T>.setupHours() {
		val lines = MutableList(0) { return@MutableList 0 }
		val labels = MutableList(0) { return@MutableList "" }
		val range: Pair<Int, Int>? = null/*RangePreference.convertToPair(
			preferences.get<String>(
				"preference_timetable_range",
				null
			)
		)*/

		user.timeGrid.days.maxByOrNull { it.units.size }?.units?.forEachIndexed { index, hour ->
			if (range?.let { index < it.first - 1 || index >= it.second } == true) return@forEachIndexed

			val startTime =
				hour.startTime.toLocalTime()
					.toString(DateTimeUtils.shortDisplayableTime())
			val endTime = hour.endTime.toLocalTime()
				.toString(DateTimeUtils.shortDisplayableTime())

			val startTimeParts = startTime.split(":")
			val endTimeParts = endTime.split(":")

			val startTimeInt =
				startTimeParts[0].toInt() * 60 + startTimeParts[1].toInt()
			val endTimeInt =
				endTimeParts[0].toInt() * 60 + endTimeParts[1].toInt()

			lines.add(startTimeInt)
			lines.add(endTimeInt)
			labels.add(hour.label)
		}

		if (!preferences.get<Boolean>("preference_timetable_range_index_reset"))
			hourIndexOffset = (range?.first ?: 1) - 1
		hourLines = lines.toIntArray()
		hourLabels = labels.toTypedArray().let { hourLabelArray ->
			if (hourLabelArray.joinToString("") == "") IntArray(
				labels.size,
				fun(idx: Int): Int { return idx + 1 }).map { it.toString() }
				.toTypedArray()
			else hourLabelArray
		}
		startTime = lines.first()
		endTime =
			lines.last() + 30 // TODO: Don't hard-code this offset
	}

	fun updateViews(container: WeekViewSwipeRefreshLayout) {
		val touchListener = View.OnTouchListener { view, motionEvent ->
			if (isAnonymous) true else view.onTouchEvent(motionEvent)
		}

		container.apply {
			isRefreshing = this@MainAppState.isRefreshing
		}

		if (!shouldUpdateWeekView) return

		container.apply {
			setOnRefreshListener { onRefresh() }
			setOnTouchListener(touchListener)
		}

		weekView.value?.apply {
			val outlineAlpha = 0.4f

			// Workaround to enable drawer gestures only when swiping from the left edge (won't work with RTL layout)
			onMotionEvent = { event -> onMotionEvent(event) }

			setOnTouchListener(touchListener)

			setPeriodChangeListener(object :
				WeekViewLoader.PeriodChangeListener<TimegridItem> {
				override fun onPeriodChange(
					startDate: LocalDate,
					endDate: LocalDate
				): List<WeekViewDisplayable<TimegridItem>> {
					return this@MainAppState.onPeriodChange(startDate, endDate)
				}
			})

			setOnEventClickListener(object :
				EventClickListener<TimegridItem> {
				override fun onEventClick(
					data: TimegridItem,
					eventRect: RectF
				) {
					timetableItemDetailsDialog.value = data
				}
			})

			scrollListener = object : ScrollListener {
				override fun onFirstVisibleDayChanged(
					newFirstVisibleDay: LocalDate,
					oldFirstVisibleDay: LocalDate?
				) {
					onScroll(newFirstVisibleDay)
				}
			}

			scaleListener = object : ScaleListener {
				override fun onScaleFinished() {
					//saveZoomLevel()
				}
			}

			setupCustomization()
			setupHours()

			config.apply {
				with(currentDensity) {
					daySeparatorColor =
						colorScheme.outline.copy(alpha = outlineAlpha)
							.toArgb()
					defaultEventColor = colorScheme.primary.toArgb()
					eventMarginVertical = 4.dp.roundToPx()
					eventPadding = 4.dp.roundToPx()
					headerRowBackgroundColor = Color.TRANSPARENT
					headerRowPadding = 8.dp.roundToPx()
					headerRowSecondaryTextColor =
						colorScheme.onSurfaceVariant.toArgb()
					headerRowSecondaryTextSize = 12.sp.toPx()
					headerRowTextColor =
						colorScheme.onSurface.toArgb()
					headerRowTextSize = 18.sp.toPx()
					headerRowTextSpacing = 10.dp.roundToPx()
					holidayTextColor =
						colorScheme.onSurface.toArgb()
					holidayTextSize = 16.sp.toPx()
					hourHeight = 72.dp.roundToPx()
					hourSeparatorColor =
						colorScheme.outline.copy(alpha = outlineAlpha)
							.toArgb()
					nowLineStrokeWidth = 2.dp.toPx()
					scrollDuration = 100
					showHourSeparator = true
					showNowLine = true
					timeColumnBackground = Color.TRANSPARENT
					timeColumnCaptionColor =
						colorScheme.onSurface.toArgb()
					timeColumnCaptionSize = 16.sp.toPx()
					timeColumnPadding = 4.dp.roundToPx()
					timeColumnTextColor =
						colorScheme.onSurfaceVariant.toArgb()
					timeColumnTextSize = 12.sp.toPx()
					todayHeaderTextColor =
						colorScheme.primary.toArgb()
					topLeftCornerDrawable =
						AppCompatResources.getDrawable(
							context,
							R.drawable.all_calendar_adjusted
						)
					topLeftCornerPadding = 4.dp.roundToPx()
					topLeftCornerTint =
						colorScheme.onSurface.toArgb()
				}
			}

			weeklyTimetableItems.clear()
			notifyDataSetChanged()
		}

		shouldUpdateWeekView = false
	}

	fun onMotionEvent(event: MotionEvent) {
		when (event.action) {
			MotionEvent.ACTION_DOWN -> {
				drawerGestures =
					event.x < with(currentDensity) { 48.dp.toPx() }
			}
			MotionEvent.ACTION_UP -> {
				drawerGestures = true
			}
		}
	}

	fun onPeriodChange(
		startDate: LocalDate,
		endDate: LocalDate
	): List<WeekViewDisplayable<TimegridItem>> {
		val weekIndex =
			convertDateTimeToWeekIndex(startDate)
		return weeklyTimetableItems[weekIndex]?.items
			?: run {
				weeklyTimetableItems[weekIndex] =
					WeeklyTimetableItems()
				displayedElement.value?.let { displayedElement ->
					coroutineScope.launch {
						loading.value++
						loadWeeklyTimetableItems(
							timetableLoader,
							startDate,
							endDate,
							displayedElement,
							colorScheme,
							onItemsChanged = { items ->
								weeklyTimetableItems[weekIndex] =
									items
								weekView.value?.notifyDataSetChanged()
							}
						)
						loading.value--
					}
				}
				emptyList()
			}
	}

	fun onScroll(newFirstVisibleDay: LocalDate) {
		currentWeekIndex.value = convertDateTimeToWeekIndex(newFirstVisibleDay)
		lastRefreshTimestamp.value = weeklyTimetableItems[currentWeekIndex.value]?.lastUpdated ?: 0
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberMainAppState(
	user: UserDatabase.User,
	timetableDatabaseInterface: TimetableDatabaseInterface,
	weekViewSwipeRefresh: MutableState<WeekViewSwipeRefreshLayout?> = remember { mutableStateOf(null) },
	weekView: MutableState<WeekView<TimegridItem>?> = remember { mutableStateOf(null) },
	context: Context = LocalContext.current,
	coroutineScope: CoroutineScope = rememberCoroutineScope(),
	colorScheme: ColorScheme = MaterialTheme.colorScheme,
	currentDensity: Density = LocalDensity.current,
	personalTimetable: Pair<PeriodElement?, String?>? = getPersonalTimetableElement(
		user,
		context,
		timetableDatabaseInterface
	),
	defaultDisplayedName: String = stringResource(id = R.string.app_name),
	drawerState: DrawerState = rememberDrawerState(DrawerValue.Closed),
	drawerGestures: MutableState<Boolean> = remember { mutableStateOf(true) },
	loading: MutableState<Int> = remember { mutableStateOf(0) },
	currentWeekIndex: MutableState<Int> = remember { mutableStateOf(0) },
	lastRefreshTimestamp: MutableState<Long> = remember { mutableStateOf(0L) },
	weeklyTimetableItems: SnapshotStateMap<Int, MainAppState.WeeklyTimetableItems?> = remember { mutableStateMapOf() },
	timetableLoader: TimetableLoader = TimetableLoader(
		context = WeakReference(context),
		user = user,
		timetableDatabaseInterface = timetableDatabaseInterface
	),
	timetableItemDetailsDialog: MutableState<TimegridItem?> = remember { mutableStateOf(null) },
	profileManagementDialog: MutableState<Boolean> = remember { mutableStateOf(false) },
) = remember(user) {
	MainAppState(
		user = user,
		timetableDatabaseInterface = timetableDatabaseInterface,
		weekViewSwipeRefresh = weekViewSwipeRefresh,
		weekView = weekView,
		context = context,
		preferences = PreferenceHelper(context).apply { loadSavedProfile() },
		coroutineScope = coroutineScope,
		colorScheme = colorScheme,
		currentDensity = currentDensity,
		personalTimetable = personalTimetable,
		defaultDisplayedName = defaultDisplayedName,
		drawerState = drawerState,
		drawerGestureState = drawerGestures,
		loading = loading,
		currentWeekIndex = currentWeekIndex,
		lastRefreshTimestamp = lastRefreshTimestamp,
		weeklyTimetableItems = weeklyTimetableItems,
		timetableLoader = timetableLoader,
		timetableItemDetailsDialog = timetableItemDetailsDialog,
		profileManagementDialog = profileManagementDialog
	)
}

@Composable
private fun getPersonalTimetableElement(
	user: UserDatabase.User,
	context: Context,
	timetableDatabaseInterface: TimetableDatabaseInterface
): Pair<PeriodElement?, String?>? {
	val customTimetable = ""//preferences["preference_timetable_personal_timetable", ""]

	return if (customTimetable.isEmpty()) {
		user.userData.elemType?.let { type ->
			PeriodElement(
				type = type,
				id = user.userData.elemId,
				orgId = user.userData.elemId,
			) to user.getDisplayedName(context)
		}
	} else {
		val displayedElement = decodeStoredTimetableValue(customTimetable)

		displayedElement to displayedElement?.let {
			timetableDatabaseInterface.getLongName(it)
		}
	}
}

private val Int.zeroToNull: Int?
	get() = if (this != 0) this else null
