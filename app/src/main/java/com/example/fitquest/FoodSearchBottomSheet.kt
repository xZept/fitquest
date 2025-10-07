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
    private var recentJob: Job? = null


    // Create adapter only after viewLifecycleOwner exists
    private lateinit var adapter: FoodSearchAdapter

    override fun onCreateDialog(savedInstanceState: Bundle?) =
        (super.onCreateDialog(savedInstanceState) as BottomSheetDialog).apply {
            setOnShowListener {
                val sheet = findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
                val behavior = BottomSheetBehavior.from(sheet!!)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = DialogFoodSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = FoodSearchAdapter(scope = viewLifecycleOwner.lifecycleScope, repo = repo) { item ->
            onPicked(item)
            dismiss()
        }
        binding.rvResults.layoutManager = LinearLayoutManager(requireContext())
        binding.rvResults.adapter = adapter

        // initial populate: show recent foods
        showRecents(limit = 10)

        binding.etSearch.addTextChangedListener { queryFlow.value = it?.toString().orEmpty() }

        queryFlow
            .debounce(400)
            .onEach { term ->
                if (term.length >= 2) {
                    performSearch(term)
                } else {
                    showRecents()
                }
            }
            .launchIn(viewLifecycleOwner.lifecycleScope)

        binding.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val t = binding.etSearch.text?.toString().orEmpty()
                if (t.length >= 2) performSearch(t) else showRecents()
                true
            } else false
        }

        binding.etSearch.requestFocus()
    }

    private fun showRecents(limit: Int = 20) {
        binding.progress.isVisible = true
        binding.tvEmpty.isVisible = false
        binding.rvResults.isVisible = false

        // cancel any in-flight search, then load recents
        searchJob?.cancel()
        recentJob?.cancel()
        recentJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                val items = withContext(Dispatchers.IO) { repo.recentFoodsAsSearchItems(limit) }
                adapter.submit(items)
                binding.rvResults.isVisible = items.isNotEmpty()
                binding.tvEmpty.isVisible = items.isEmpty()
                binding.tvEmpty.text = if (items.isEmpty()) "No recent foods yet." else ""
            } catch (e: Exception) {
                android.util.Log.e("FoodSearch", "Load recents failed", e)
                adapter.submit(emptyList())
                binding.rvResults.isVisible = false
                binding.tvEmpty.isVisible = true
                binding.tvEmpty.text = "Couldn't load recent foods."
            } finally {
                binding.progress.isVisible = false
            }
        }
    }

    private fun performSearch(term: String) {
        if (term.isBlank()) return
        binding.progress.isVisible = true
        binding.tvEmpty.isVisible = false
        binding.rvResults.isVisible = false

        searchJob?.cancel()
        searchJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                val items = withContext(Dispatchers.IO) {
                    repo.search(term, page = 1, pageSize = 50)
                }

                // Log and render
                android.util.Log.d("FoodSearch", "results=${items.size} for '$term'")
                adapter.submit(items)
                android.util.Log.d("FoodSearch", "adapter itemCount=${adapter.itemCount}")

                binding.rvResults.isVisible = items.isNotEmpty()
                binding.tvEmpty.isVisible = items.isEmpty()
                binding.tvEmpty.text = if (items.isEmpty()) "No results." else ""
            } catch (ce: CancellationException) {
                // normal during fast typing
                throw ce
            } catch (he: retrofit2.HttpException) {
                val code = he.code()
                val body = withContext(Dispatchers.IO) { he.response()?.errorBody()?.string() }
                android.util.Log.e("FoodSearch", "HTTP $code: $body", he)
                adapter.submit(emptyList())
                android.util.Log.d("FoodSearch", "adapter itemCount=${adapter.itemCount}")
                binding.rvResults.isVisible = false
                binding.tvEmpty.isVisible = true
                binding.tvEmpty.text = when (code) {
                    401, 403 -> "API key rejected. Check your FDC key."
                    429 -> "Rate limit reached. Try again shortly."
                    else -> "Server error ($code). Try again."
                }
            } catch (e: Exception) {
                android.util.Log.e("FoodSearch", "Search failed", e)
                adapter.submit(emptyList())
                android.util.Log.d("FoodSearch", "adapter itemCount=${adapter.itemCount}")
                binding.rvResults.isVisible = false
                binding.tvEmpty.isVisible = true
                binding.tvEmpty.text = if (e is java.net.UnknownHostException || e is java.net.SocketException)
                    "No internet connection." else "Error searching. Please try again."
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
    private val scope: CoroutineScope,
    private val repo: FoodRepository,
    private val onClick: (FdcSearchFood) -> Unit
) : RecyclerView.Adapter<VH>() {

    private val items = mutableListOf<FdcSearchFood>()

    // Avoid multiple network calls while scrolling the same id
    private val textCache = mutableMapOf<Long, String>()           // fdcId -> "Protein: Xg; Fat: Yg; Carbs: Zg"
    private val inFlight  = mutableMapOf<Long, Deferred<String>>() // fdcId -> job

    fun submit(newItems: List<FdcSearchFood>) {
        val oldItems = items.toList()
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = oldItems.size
            override fun getNewListSize() = newItems.size
            override fun areItemsTheSame(old: Int, new: Int) =
                oldItems[old].fdcId == newItems[new].fdcId
            override fun areContentsTheSame(old: Int, new: Int) =
                oldItems[old] == newItems[new]
        })
        items.clear()
        items.addAll(newItems)
        diff.dispatchUpdatesTo(this)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemFoodSearchBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.bind(item) { onClick(item) }  // ← click now uses the actual item

        // Sub line: cached or fetch once
        val cached = textCache[item.fdcId]
        if (cached != null) {
            holder.bindSub(cached)
        } else {
            holder.bindSub("") // or " "
            val job = inFlight[item.fdcId] ?: scope.async(Dispatchers.IO) {
                val pm = repo.previewMacrosPer100g(item.fdcId)
                "Protein: ${pm.protein.toInt()}g; Fat: ${pm.fat.toInt()}g; Carbs: ${pm.carbs.toInt()}g"
            }.also { inFlight[item.fdcId] = it }

            scope.launch {
                try {
                    val line = job.await()
                    textCache[item.fdcId] = line
                    if (holder.bindingAdapterPosition != RecyclerView.NO_POSITION &&
                        items.getOrNull(holder.bindingAdapterPosition)?.fdcId == item.fdcId) {
                        holder.bindSub(line)
                    }
                } finally {
                    inFlight.remove(item.fdcId)
                }
            }
        }
    }
}

private class VH(
    private val b: ItemFoodSearchBinding
) : RecyclerView.ViewHolder(b.root) {

    fun bind(item: FdcSearchFood, onClick: () -> Unit) {
        b.tvTitle.text = item.description
        b.root.setOnClickListener { onClick() }
    }

    fun bindSub(text: String) {
        b.tvSub.text = text
    }
}
