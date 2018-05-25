package org.wordpress.android.ui.activitylog

import android.arch.core.executor.testing.InstantTaskExecutorRule
import com.nhaarman.mockito_kotlin.argumentCaptor
import com.nhaarman.mockito_kotlin.reset
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.wordpress.android.fluxc.Dispatcher
import org.wordpress.android.fluxc.action.ActivityLogAction.FETCH_REWIND_STATE
import org.wordpress.android.fluxc.action.ActivityLogAction.REWIND
import org.wordpress.android.fluxc.annotations.action.Action
import org.wordpress.android.fluxc.model.SiteModel
import org.wordpress.android.fluxc.model.activity.RewindStatusModel
import org.wordpress.android.fluxc.model.activity.RewindStatusModel.Rewind
import org.wordpress.android.fluxc.model.activity.RewindStatusModel.Rewind.Status.FINISHED
import org.wordpress.android.fluxc.model.activity.RewindStatusModel.Rewind.Status.RUNNING
import org.wordpress.android.fluxc.model.activity.RewindStatusModel.State.ACTIVE
import org.wordpress.android.fluxc.model.activity.RewindStatusModel.State.INACTIVE
import org.wordpress.android.fluxc.store.ActivityLogStore
import org.wordpress.android.fluxc.store.ActivityLogStore.FetchRewindStatePayload
import org.wordpress.android.fluxc.store.ActivityLogStore.OnRewind
import org.wordpress.android.fluxc.store.ActivityLogStore.OnRewindStatusFetched
import org.wordpress.android.fluxc.store.ActivityLogStore.RewindError
import org.wordpress.android.fluxc.store.ActivityLogStore.RewindErrorType
import org.wordpress.android.fluxc.store.ActivityLogStore.RewindPayload
import org.wordpress.android.fluxc.store.ActivityLogStore.RewindStatusError
import org.wordpress.android.fluxc.store.ActivityLogStore.RewindStatusErrorType.INVALID_RESPONSE
import java.util.Date

@RunWith(MockitoJUnitRunner::class)
class RewindStatusServiceTest {
    @Rule @JvmField val rule = InstantTaskExecutorRule()

    private val actionCaptor = argumentCaptor<Action<Any>>()

    @Mock private lateinit var activityLogStore: ActivityLogStore
    @Mock private lateinit var workerController: RewindStateProgressWorkerController
    @Mock private lateinit var dispatcher: Dispatcher
    @Mock private lateinit var site: SiteModel

    private lateinit var rewindStatusService: RewindStatusService
    private var rewindAvailable: Boolean? = null
    private var rewindState: Rewind? = null

    private val activeRewindStatusModel = RewindStatusModel(
            state = ACTIVE,
            lastUpdated = Date(),
            rewind = null,
            credentials = null,
            reason = null,
            canAutoconfigure = null
    )

    private val rewindInProgress = Rewind(
            rewindId = "1",
            restoreId = 10,
            status = RUNNING,
            progress = 23,
            reason = null
    )

    @Before
    fun setUp() {
        rewindStatusService = RewindStatusService(activityLogStore, workerController, dispatcher)
        rewindAvailable = null
        rewindState = null
        rewindStatusService.rewindAvailable.observeForever { rewindAvailable = it }
        rewindStatusService.rewindState.observeForever { rewindState = it }
        whenever(activityLogStore.getRewindStatusForSite(site)).thenReturn(null)
    }

    @Test
    fun emitsAvailableRewindStatusOnStartWhenActiveStatusPresent() {
        whenever(activityLogStore.getRewindStatusForSite(site)).thenReturn(activeRewindStatusModel)

        rewindStatusService.start(site)

        verify(dispatcher).register(rewindStatusService)
        assertEquals(rewindAvailable, true)
    }

    @Test
    fun emitsUnavailableRewindStatusOnStartWhenNonActiveStatusPresent() {
        val inactiveRewindStatusModel = activeRewindStatusModel.copy(state = INACTIVE)
        whenever(activityLogStore.getRewindStatusForSite(site)).thenReturn(inactiveRewindStatusModel)

        rewindStatusService.start(site)

        verify(dispatcher).register(rewindStatusService)
        assertEquals(rewindAvailable, false)
    }

    @Test
    fun emitsUnavailableRewindStatusOnStartWhenRewindInProgress() {
        val inactiveRewindStatusModel = activeRewindStatusModel.copy(rewind = rewindInProgress)
        whenever(activityLogStore.getRewindStatusForSite(site)).thenReturn(inactiveRewindStatusModel)

        rewindStatusService.start(site)

        verify(dispatcher).register(rewindStatusService)
        assertEquals(rewindAvailable, false)
    }

    @Test
    fun triggersFetchWhenRewindStatusNotAvailable() {
        rewindStatusService.start(site)

        verify(dispatcher).register(rewindStatusService)
        assertFetchRewindStatusAction()
    }

    @Test
    fun unregistersOnStop() {
        rewindStatusService.stop()

        verify(dispatcher).unregister(rewindStatusService)
    }

    @Test
    fun triggersRewindAndMakesActionUnavailable() {
        val rewindId = "10"

        rewindStatusService.rewind(rewindId, site)

        assertRewindAction(rewindId)
        assertEquals(false, rewindAvailable)
        assertNull(rewindState)
    }

    @Test
    fun cancelsWorkerOnFetchErrorRewindState() {
        val error = RewindStatusError(INVALID_RESPONSE, null)
        rewindStatusService.onRewindStatusFetched(OnRewindStatusFetched(error, REWIND))

        verify(workerController).cancelWorker()
    }

    @Test
    fun onRewindStateInProgressUpdateState() {
        rewindStatusService.start(site)

        val rewindStatusInProgress = activeRewindStatusModel.copy(rewind = rewindInProgress)
        whenever(activityLogStore.getRewindStatusForSite(site)).thenReturn(rewindStatusInProgress)

        rewindStatusService.onRewindStatusFetched(OnRewindStatusFetched(FETCH_REWIND_STATE))

        assertEquals(rewindAvailable, false)
        assertEquals(rewindState, rewindInProgress)
    }

    @Test
    fun onRewindStateFinishedUpdateStateAndCancelWorker() {
        rewindStatusService.start(site)

        val rewindFinished = rewindInProgress.copy(status = FINISHED, progress = 100)
        val rewindStatusInProgress = activeRewindStatusModel.copy(rewind = rewindFinished)
        whenever(activityLogStore.getRewindStatusForSite(site)).thenReturn(rewindStatusInProgress)

        rewindStatusService.onRewindStatusFetched(OnRewindStatusFetched(FETCH_REWIND_STATE))

        assertEquals(rewindAvailable, true)
        assertEquals(rewindState, rewindFinished)
        verify(workerController).cancelWorker()
    }

    @Test
    fun onRewindErrorCancelWorkerAndReenableRewindStatus() {
        rewindStatusService.start(site)
        reset(dispatcher)

        whenever(activityLogStore.getRewindStatusForSite(site)).thenReturn(activeRewindStatusModel)

        rewindStatusService.onRewind(OnRewind(RewindError(RewindErrorType.INVALID_RESPONSE, null), REWIND))

        verify(workerController).cancelWorker()
        assertEquals(rewindAvailable, true)
        assertNull(rewindState)
    }

    @Test
    fun onRewindFetchStatusAndStartWorker() {
        rewindStatusService.start(site)
        reset(dispatcher)

        rewindStatusService.onRewind(OnRewind("10", REWIND))

        verify(workerController).startWorker(site, 10)
    }

    private fun assertFetchRewindStatusAction() {
        verify(dispatcher).dispatch(actionCaptor.capture())
        actionCaptor.firstValue.apply {
            assertEquals(FETCH_REWIND_STATE, this.type)
            assertTrue(this.payload is FetchRewindStatePayload)
            (this.payload as FetchRewindStatePayload).apply {
                assertEquals(this.site, site)
            }
        }
    }

    private fun assertRewindAction(rewindId: String) {
        verify(dispatcher).dispatch(actionCaptor.capture())
        actionCaptor.firstValue.apply {
            assertEquals(REWIND, this.type)
            assertTrue(this.payload is RewindPayload)
            (this.payload as RewindPayload).apply {
                assertEquals(this.site, site)
                assertEquals(this.rewindId, rewindId)
            }
        }
    }
}
