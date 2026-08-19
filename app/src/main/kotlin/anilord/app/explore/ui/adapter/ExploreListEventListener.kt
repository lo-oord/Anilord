package anilord.app.explore.ui.adapter

import android.view.View
import anilord.app.list.ui.adapter.ListHeaderClickListener
import anilord.app.list.ui.adapter.ListStateHolderListener

interface ExploreListEventListener : ListStateHolderListener, View.OnClickListener, ListHeaderClickListener
