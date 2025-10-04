package com.example.fitquest

import android.os.Bundle
import android.view.*
import android.view.inputmethod.EditorInfo
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.fitquest.fdc.FdcModels.FdcSearchFood
import com.example.fitquest.data.repository.FoodRepository
import com.example.fitquest.databinding.DialogFoodSearchBinding
import com.example.fitquest.databinding.ItemFoodSearchBinding
import kotlinx.coroutines.*
import androidx.lifecycle.lifecycleScope
import com.example.fitquest.fdc.FdcModels
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class FoodSearchBottomSheet(
    private val repo: FoodRepository,
    private val onPicked: (FdcModels.FdcSearchFood) -> Unit
) : BottomSheetDialogFragment() {

    private var _binding: DialogFoodSearchBinding? = null
    private val binding get() = _binding!!

    private val queryFlow = MutableStateFlow("")
    private var searchJob: Job? = null
    private val adapter = FoodSearchAdapter { item ->
        onPicked(item)
        dismiss()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?) =
        (super.onCreateDialog(savedInstanceState) as BottomSheetDialog).apply {
            setOnShowListener {
                val sheet = findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
                val behavior = BottomSheetBehavior.from(sheet!!)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
            }
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogFoodSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.rvResults.layoutManager = LinearLayoutManager(requireContext())
        binding.rvResults.adapter = adapter

        // Debounced text change
        binding.etSearch.addTextChangedListener {
            queryFlow.value = it?.toString().orEmpty()
        }
        queryFlow
            .debounce(300)
            .filter { it.length >= 2 }
            .onEach { performSearch(it) }
            .launchIn(viewLifecycleOwner.lifecycleScope)

        // IME search action
        binding.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch(binding.etSearch.text?.toString().orEmpty())
                true
            } else false
        }

        // Auto focus + keyboard
        binding.etSearch.requestFocus()
    }

    private fun performSearch(term: String) {
        if (term.isBlank()) return
        binding.progress.isVisible = true
        binding.tvEmpty.isVisible = false
        searchJob?.cancel()

        searchJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                val items = withContext(Dispatchers.IO) {
                    repo.search(term, page = 1, pageSize = 50)
                }
                adapter.submit(items)
                binding.tvEmpty.isVisible = items.isEmpty()
            } catch (ce: CancellationException) {
                throw ce
            } catch (he: retrofit2.HttpException) {
                val code = he.code()
                val body = withContext(Dispatchers.IO) { he.response()?.errorBody()?.string() }
                android.util.Log.e("FoodSearch", "HTTP $code: $body", he)
                binding.tvEmpty.isVisible = true
                binding.tvEmpty.text = when (code) {
                    401, 403 -> "API key rejected. Check your FDC key."
                    429 -> "Rate limit reached. Try again shortly."
                    else -> "Server error ($code). Try again."
                }
                adapter.submit(emptyList())
            } catch (e: Exception) {
                android.util.Log.e("FoodSearch", "Search failed", e)
                binding.tvEmpty.isVisible = true
                binding.tvEmpty.text = when {
                    e is java.net.UnknownHostException ||
                            e is java.net.SocketException     -> "No internet connection."
                    else                               -> "Error searching. Please try again."
                }
                adapter.submit(emptyList())
            } finally {
                binding.progress.isVisible = false
            }
        }
    }



    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        searchJob?.cancel()
    }
}

private class FoodSearchAdapter(
    val onClick: (FdcSearchFood) -> Unit
) : RecyclerView.Adapter<VH>() {

    private val items = mutableListOf<FdcSearchFood>()

    fun submit(newItems: List<FdcSearchFood>) {
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = items.size
            override fun getNewListSize() = newItems.size
            override fun areItemsTheSame(old: Int, new: Int) = items[old].fdcId == newItems[new].fdcId
            override fun areContentsTheSame(old: Int, new: Int) = items[old] == newItems[new]
        })
        items.clear(); items.addAll(newItems)
        diff.dispatchUpdatesTo(this)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemFoodSearchBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding, onClick)
    }

    override fun getItemCount() = items.size
    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])
}

private class VH(
    private val b: ItemFoodSearchBinding,
    val onClick: (FdcSearchFood) -> Unit
) : RecyclerView.ViewHolder(b.root) {
    fun bind(item: FdcSearchFood) {
        b.tvTitle.text = item.description
        b.tvSub.text = item.dataType ?: ""
        b.root.setOnClickListener {
            b.root.isEnabled = false
            onClick(item)
            b.root.postDelayed({ b.root.isEnabled = true }, 500)
        }
    }
}