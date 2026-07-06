package com.gamecenter.app.wrongbook.ui

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gamecenter.app.wrongbook.R
import com.gamecenter.app.wrongbook.data.SubjectEntity
import com.gamecenter.app.wrongbook.databinding.FragmentSubjectManagementBinding
import com.gamecenter.app.wrongbook.databinding.ItemSubjectManageBinding

class SubjectManagementFragment : DialogFragment() {

    private var _binding: FragmentSubjectManagementBinding? = null
    private val binding get() = _binding!!

    private val viewModel: WrongBookViewModel by activityViewModels()
    private lateinit var adapter: SubjectAdapter

    private val moduleResources: android.content.res.Resources
        get() = com.gamecenter.app.modules.ModuleManager.getModuleResources(ModuleContextHelper.MODULE_ID)?.resources
            ?: super.getResources()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.Theme_GameMatrixApp)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val moduleInflater = ModuleContextHelper.getLayoutInflater(requireContext())
        _binding = FragmentSubjectManagementBinding.inflate(moduleInflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.let { window ->
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupListeners()
        setupObservers()
    }

    private fun setupRecyclerView() {
        adapter = SubjectAdapter(
            onEdit = { subject -> showEditDialog(subject) },
            onDelete = { subject -> showDeleteDialog(subject) }
        )

        binding.recyclerSubjects.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerSubjects.adapter = adapter

        // 设置拖拽排序 ItemTouchHelper
        val touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPos = viewHolder.adapterPosition
                val toPos = target.adapterPosition
                val list = adapter.subjects.toMutableList()
                val temp = list[fromPos]
                list[fromPos] = list[toPos]
                list[toPos] = temp
                adapter.subjects = list
                adapter.notifyItemMoved(fromPos, toPos)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                // 拖拽释放后，批量保存排序到数据库
                adapter.subjects.forEachIndexed { index, subject ->
                    viewModel.updateSubject(subject.copy(sortOrder = index))
                }
            }
        })
        touchHelper.attachToRecyclerView(binding.recyclerSubjects)
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            dismiss()
        }

        binding.btnAddSubject.setOnClickListener {
            showAddDialog()
        }
    }

    private fun setupObservers() {
        viewModel.subjects.observe(viewLifecycleOwner) { list ->
            adapter.subjects = list.sortedBy { it.sortOrder }
            adapter.notifyDataSetChanged()
            binding.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun showAddDialog() {
        val input = EditText(requireContext()).apply {
            hint = "请输入科目名称"
            setSingleLine(true)
        }
        AlertDialog.Builder(requireContext())
            .setTitle("新增科目")
            .setView(input)
            .setPositiveButton("确定") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    if (viewModel.subjects.value?.any { it.name.equals(name, true) } == true) {
                        Toast.makeText(requireContext(), "科目已存在", Toast.LENGTH_SHORT).show()
                    } else {
                        viewModel.ensureSubject(name)
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showEditDialog(subject: SubjectEntity) {
        val input = EditText(requireContext()).apply {
            setText(subject.name)
            setSingleLine(true)
            setSelection(subject.name.length)
        }
        AlertDialog.Builder(requireContext())
            .setTitle("重命名科目")
            .setView(input)
            .setPositiveButton("确定") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty() && name != subject.name) {
                    if (viewModel.subjects.value?.any { it.name.equals(name, true) } == true) {
                        Toast.makeText(requireContext(), "科目名称已存在", Toast.LENGTH_SHORT).show()
                    } else {
                        viewModel.updateSubject(subject.copy(name = name))
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showDeleteDialog(subject: SubjectEntity) {
        AlertDialog.Builder(requireContext())
            .setTitle("删除科目")
            .setMessage("确定删除科目“${subject.name}”吗？这不会删除该科目下的错题，错题会自动划分到“通用”。")
            .setPositiveButton("删除") { _, _ ->
                viewModel.deleteSubject(subject)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ===== RecyclerView Adapter =====
    private class SubjectAdapter(
        private val onEdit: (SubjectEntity) -> Unit,
        private val onDelete: (SubjectEntity) -> Unit
    ) : RecyclerView.Adapter<SubjectAdapter.ViewHolder>() {

        var subjects = emptyList<SubjectEntity>()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemSubjectManageBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(subjects[position])
        }

        override fun getItemCount(): Int = subjects.size

        inner class ViewHolder(
            private val binding: ItemSubjectManageBinding
        ) : RecyclerView.ViewHolder(binding.root) {

            fun bind(item: SubjectEntity) {
                binding.tvSubjectName.text = item.name

                binding.btnEdit.setOnClickListener { onEdit(item) }
                binding.btnDelete.setOnClickListener { onDelete(item) }
            }
        }
    }
}
