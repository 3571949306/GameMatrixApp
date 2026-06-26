import os

xml_path = r'd:\Developmment\GameMatrixApp\app\src\main\res\layout\item_module.xml'

try:
    with open(xml_path, 'r', encoding='utf-8') as f:
        content = f.read()
except Exception as e:
    print(f"Error reading {xml_path}: {e}")
    exit(1)

old_title_row = """            <!-- 标题行：名称 + 分类标签 -->
            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="horizontal"
                android:gravity="center_vertical">

                <TextView
                    android:id="@+id/moduleItemName"
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:textColor="?android:attr/textColorPrimary"
                    android:textSize="15sp"
                    android:textStyle="bold"
                    android:maxLines="1"
                    android:ellipsize="end" />

                <com.google.android.material.chip.Chip
                    android:id="@+id/moduleItemCategoryChip"
                    style="@style/Widget.Material3.Chip.Assist"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginStart="8dp"
                    android:textSize="10sp"
                    android:visibility="gone" />
            </LinearLayout>"""

new_title_row = """            <!-- 标题行：名称 + 分类标签 -->
            <androidx.constraintlayout.widget.ConstraintLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content">

                <TextView
                    android:id="@+id/moduleItemName"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:textColor="?android:attr/textColorPrimary"
                    android:textSize="15sp"
                    android:textStyle="bold"
                    android:maxLines="1"
                    android:ellipsize="end"
                    app:layout_constrainedWidth="true"
                    app:layout_constraintHorizontal_bias="0.0"
                    app:layout_constraintHorizontal_chainStyle="packed"
                    app:layout_constraintStart_toStartOf="parent"
                    app:layout_constraintEnd_toStartOf="@+id/moduleItemCategoryChip"
                    app:layout_constraintTop_toTopOf="parent"
                    app:layout_constraintBottom_toBottomOf="parent" />

                <com.google.android.material.chip.Chip
                    android:id="@+id/moduleItemCategoryChip"
                    style="@style/Widget.Material3.Chip.Assist"
                    android:layout_width="wrap_content"
                    android:layout_height="22dp"
                    android:layout_marginStart="8dp"
                    android:textSize="10sp"
                    android:visibility="gone"
                    app:chipCornerRadius="4dp"
                    app:chipMinHeight="20dp"
                    app:textEndPadding="6dp"
                    app:textStartPadding="6dp"
                    app:layout_constraintStart_toEndOf="@+id/moduleItemName"
                    app:layout_constraintEnd_toStartOf="@+id/moduleItemBuiltInChip"
                    app:layout_constraintTop_toTopOf="parent"
                    app:layout_constraintBottom_toBottomOf="parent" />

                <com.google.android.material.chip.Chip
                    android:id="@+id/moduleItemBuiltInChip"
                    style="@style/Widget.Material3.Chip.Assist"
                    android:layout_width="wrap_content"
                    android:layout_height="22dp"
                    android:layout_marginStart="6dp"
                    android:text="内置"
                    android:textSize="10sp"
                    android:visibility="gone"
                    app:chipCornerRadius="4dp"
                    app:chipMinHeight="20dp"
                    app:textEndPadding="6dp"
                    app:textStartPadding="6dp"
                    app:layout_constraintStart_toEndOf="@+id/moduleItemCategoryChip"
                    app:layout_constraintTop_toTopOf="parent"
                    app:layout_constraintBottom_toBottomOf="parent" />
            </androidx.constraintlayout.widget.ConstraintLayout>"""

old_builtin_chip = """        <!-- 内置标签（如果适用） -->
        <com.google.android.material.chip.Chip
            android:id="@+id/moduleItemBuiltInChip"
            style="@style/Widget.Material3.Chip.Assist"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="内置"
            android:textSize="10sp"
            android:visibility="gone"
            app:layout_constraintEnd_toEndOf="parent"
            app:layout_constraintTop_toTopOf="parent"
            android:layout_marginEnd="12dp"
            android:layout_marginTop="8dp" />"""

if old_title_row in content:
    content = content.replace(old_title_row, new_title_row)
else:
    print("Could not find old_title_row")

if old_builtin_chip in content:
    content = content.replace(old_builtin_chip, "")
else:
    print("Could not find old_builtin_chip")

with open(xml_path, 'w', encoding='utf-8') as f:
    f.write(content)

print("item_module.xml updated successfully.")
