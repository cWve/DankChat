package com.flxrs.dankchat.chat.suggestion

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import coil.clear
import coil.size.Scale
import com.flxrs.dankchat.R
import com.flxrs.dankchat.utils.extensions.getDrawableAndSetSurfaceTint
import com.flxrs.dankchat.utils.extensions.loadImage

class EmoteSuggestionsArrayAdapter(
    context: Context,
    private val onCount: (count: Int) -> Unit
) : ArrayAdapter<Suggestion>(context, R.layout.emote_suggestion_item, R.id.suggestion_text) {
    override fun getCount(): Int = super.getCount().also { onCount(it) }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = super.getView(position, convertView, parent)
        val textView = view.findViewById<TextView>(R.id.suggestion_text)
        val imageView = view.findViewById<ImageView>(R.id.suggestion_image)

        imageView.clear()
        imageView.setImageDrawable(null)
        getItem(position)?.let { suggestion: Suggestion ->
            when (suggestion) {
                is Suggestion.EmoteSuggestion   -> imageView.loadImage(suggestion.emote.url) {
                    scale(Scale.FIT)
                    size(textView.lineHeight * 2)
                }
                is Suggestion.UserSuggestion    -> imageView.setImageDrawable(context.getDrawableAndSetSurfaceTint(R.drawable.ic_notification_icon))
                is Suggestion.CommandSuggestion -> imageView.setImageDrawable(context.getDrawableAndSetSurfaceTint(R.drawable.ic_android))
            }
        }

        return view
    }
}