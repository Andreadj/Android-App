package com.mobiled.android.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.mobiled.android.R
import com.mobiled.android.databinding.EffectItemBinding
import com.mobiled.android.model.Effect

class EffectListAdapter(var effectList: List<Effect>) :
    RecyclerView.Adapter<ViewHolder<EffectItemBinding>>() {

    var itemListener: ItemListener? = null;

    var selectedHolder: ViewHolder<EffectItemBinding>? = null


    var currentSelectedEffect = -1

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder<EffectItemBinding> {
        return ViewHolder(EffectItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun getItemCount(): Int = effectList.size

    override fun onBindViewHolder(holder: ViewHolder<EffectItemBinding>, position: Int) {
        var effect = effectList[holder.bindingAdapterPosition]
        holder.viewBinding.ivEffect.setImageResource(effect.effectResource)
        holder.viewBinding.tvEffect.setText(effect.name)

        holder.itemView.setOnClickListener {
            if (selectedHolder != null && selectedHolder!!.bindingAdapterPosition != holder.bindingAdapterPosition) {
                selectedHolder?.viewBinding?.root?.setBackgroundResource(R.drawable.grey_border_box)
            }

            if (!(selectedHolder?.bindingAdapterPosition == holder.bindingAdapterPosition)) {
                selectedHolder = holder
                selectedHolder?.viewBinding?.root?.setBackgroundResource(R.drawable.white_border_box)
            }

            itemListener?.onItemPressed(effect)
        }

        if (currentSelectedEffect != -1 && (holder.bindingAdapterPosition + 1) == currentSelectedEffect) {
            currentSelectedEffect = -1
            selectedHolder = holder
            selectedHolder?.viewBinding?.root?.setBackgroundResource(R.drawable.white_border_box)
        }
    }

    fun bindListener(itemListener: ItemListener) {
        this.itemListener = itemListener;
    }

    public interface ItemListener {
        fun onItemPressed(effect: Effect)
    }
}