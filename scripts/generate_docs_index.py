import os
import re

def get_md_title(filepath):
    try:
        with open(filepath, 'r', encoding='utf-8') as f:
            for line in f:
                if line.startswith('# '):
                    return line.strip('# \n')
    except:
        pass
    return os.path.basename(filepath)

def generate_index(root_dir):
    index_content = "# GameMatrixApp 全局文档索引\n\n自动生成的文档地图。此文件作为项目全部文档的中枢链接。\n\n"
    
    md_files_by_dir = {}
    
    for root, dirs, files in os.walk(root_dir):
        # Exclude build dirs and hidden dirs
        if '.git' in root or '.gradle' in root or 'build' in root:
            continue
            
        rel_dir = os.path.relpath(root, root_dir)
        rel_dir = rel_dir.replace('\\', '/')
        if rel_dir == '.':
            rel_dir = 'Root'
            
        md_files = [f for f in files if f.endswith('.md')]
        if md_files:
            md_files_by_dir[rel_dir] = []
            for mf in md_files:
                filepath = os.path.join(root, mf)
                rel_path = os.path.relpath(filepath, root_dir).replace('\\', '/')
                title = get_md_title(filepath)
                md_files_by_dir[rel_dir].append({'path': '/' + rel_path, 'title': title, 'filename': mf})

    for d in sorted(md_files_by_dir.keys()):
        if d == 'Root':
            index_content += "## 根目录 (Root)\n\n"
        else:
            index_content += f"## {d}\n\n"
            
        index_content += "| 文档 | 描述 |\n|---|---|\n"
        for item in sorted(md_files_by_dir[d], key=lambda x: x['filename']):
            index_content += f"| [{item['filename']}]({item['path']}) | {item['title']} |\n"
        index_content += "\n"
        
    with open(os.path.join(root_dir, 'docs', 'DOCUMENTATION_INDEX.md'), 'w', encoding='utf-8') as f:
        f.write(index_content)
        
    print("DOCUMENTATION_INDEX.md generated successfully.")

if __name__ == '__main__':
    generate_index(r'd:\Developmment\GameMatrixApp')
