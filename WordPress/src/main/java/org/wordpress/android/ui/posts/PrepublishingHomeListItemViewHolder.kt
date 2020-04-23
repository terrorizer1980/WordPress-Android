package org.wordpress.android.ui.posts

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.recyclerview.widget.RecyclerView
import org.wordpress.android.R
import org.wordpress.android.ui.posts.PrepublishingActionItemUiState.PrepublishingActionUiState
import org.wordpress.android.ui.posts.PrepublishingActionItemUiState.PrepublishingHomeHeaderUiState
import org.wordpress.android.ui.utils.UiHelpers
import org.wordpress.android.util.image.ImageManager

private const val ROUNDING_RADIUS_DP = 10

sealed class PrepublishingHomeViewHolder(internal val parent: ViewGroup, @LayoutRes layout: Int) :
        RecyclerView.ViewHolder(
                LayoutInflater.from(parent.context).inflate(
                        layout,
                        parent,
                        false
                )
        ) {
    abstract fun onBind(uiState: PrepublishingActionItemUiState)

    class PrepublishingHomeListItemViewHolder(parentView: ViewGroup, val uiHelpers: UiHelpers) :
            PrepublishingHomeViewHolder(
                    parentView, R.layout.prepublishing_action_list_item
            ) {
        private val actionType: TextView = itemView.findViewById(R.id.action_type)
        private val actionResult: TextView = itemView.findViewById(R.id.action_result)

        override fun onBind(uiState: PrepublishingActionItemUiState) {
            uiState as PrepublishingActionUiState

            actionType.text = uiHelpers.getTextOfUiString(itemView.context, uiState.actionType.textRes)
            uiState.actionResult?.let { resultText ->
                actionResult.text = uiHelpers.getTextOfUiString(itemView.context, resultText)
            }
        }
    }

    class PrepublishingHeaderListItemViewHolder(
        parentView: ViewGroup,
        val uiHelpers: UiHelpers,
        val imageManager: ImageManager
    ) :
            PrepublishingHomeViewHolder(
                    parentView, R.layout.prepublishing_home_header_list_item
            ) {
        private val siteName: TextView = itemView.findViewById(R.id.site_name)
        private val siteIcon: ImageView = itemView.findViewById(R.id.site_icon)

        override fun onBind(uiState: PrepublishingActionItemUiState) {
            uiState as PrepublishingHomeHeaderUiState

            siteName.text = uiHelpers.getTextOfUiString(itemView.context, uiState.siteName)
            /*imageManager.loadWithRoundedCorners(
                    siteIcon,
                    BLAVATAR,
                    uiState.siteIconUrl,
                    DisplayUtils.dpToPx(itemView.context, ROUNDING_RADIUS_DP)
            )*/
        }
    }
}