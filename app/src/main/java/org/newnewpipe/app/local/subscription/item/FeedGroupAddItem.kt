package org.newnewpipe.app.local.subscription.item

import android.view.View
import com.xwray.groupie.viewbinding.BindableItem
import org.newnewpipe.app.R
import org.newnewpipe.app.databinding.FeedGroupAddNewItemBinding

class FeedGroupAddItem : BindableItem<FeedGroupAddNewItemBinding>() {
    override fun getLayout(): Int = R.layout.feed_group_add_new_item
    override fun bind(viewBinding: FeedGroupAddNewItemBinding, position: Int) {}
    override fun initializeViewBinding(view: View) = FeedGroupAddNewItemBinding.bind(view)
}
