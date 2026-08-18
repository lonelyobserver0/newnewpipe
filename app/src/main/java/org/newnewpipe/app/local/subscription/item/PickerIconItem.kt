package org.newnewpipe.app.local.subscription.item

import android.view.View
import androidx.annotation.DrawableRes
import com.xwray.groupie.viewbinding.BindableItem
import org.newnewpipe.app.R
import org.newnewpipe.app.databinding.PickerIconItemBinding
import org.newnewpipe.app.local.subscription.FeedGroupIcon

class PickerIconItem(
    val icon: FeedGroupIcon
) : BindableItem<PickerIconItemBinding>() {
    @DrawableRes
    val iconRes: Int = icon.getDrawableRes()

    override fun getLayout(): Int = R.layout.picker_icon_item

    override fun bind(viewBinding: PickerIconItemBinding, position: Int) {
        viewBinding.iconView.setImageResource(iconRes)
    }

    override fun initializeViewBinding(view: View) = PickerIconItemBinding.bind(view)
}
