package org.manga.peak.explore.ui.adapter

import android.view.View
import org.manga.peak.list.ui.adapter.ListHeaderClickListener
import org.manga.peak.list.ui.adapter.ListStateHolderListener

interface ExploreListEventListener : ListStateHolderListener, View.OnClickListener, ListHeaderClickListener
