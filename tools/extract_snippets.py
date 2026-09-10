#!/usr/bin/env python3
"""从 <repo>/*.md 里抽取被显式标记的 Kotlin 代码块，生成可编译的 Kotlin 源码。

用法
----
    python3 tools/extract_snippets.py

随后编译校验（需要 Android SDK）：

    cd snippets && ./gradlew compileDebugKotlin

另有两条与“普通编译”分开的检查：

    cd snippets && ./gradlew compileDebugKotlin -PsnippetsStrictWarnings --rerun-tasks
        严格模式：把编译器警告（主要是已弃用 API）当错误。

    python3 tools/extract_snippets.py --no-file-optin && cd snippets && ./gradlew compileDebugKotlin
        opt-in 审计：故意不代片段声明 @file:OptIn(...)，编译失败的信息会列出
        哪些片段依赖实验性 API，人工核对这些章节是否说明了需要 opt-in。
        跑完记得重新执行不带参数的抽取。

标记方式（对作者，二选一）
-------------------------
1) 代码块前一行写 HTML 注释（渲染后不可见，推荐）：

       <!-- compile -->
       ```kotlin
       Text("hi")
       ```

2) 把标记写进代码块的信息串：

       ```kotlin compile
       Text("hi")
       ```

**只有被标记的代码块才会参与编译校验**，未标记的块（节选、伪代码、依赖
项目专属符号的片段）原样保留、不做任何处理。

可选的注释指令
--------------
    <!-- compile decls -->
        “声明模式”：片段本身是完整声明（@Composable fun / class / enum class /
        @Composable 成员等），直接作为包装 object 的成员展开，而不是塞进
        一个 @Composable 函数体（函数体里不允许 local enum / interface）。

    <!-- compile
    prelude: var liked by remember { mutableStateOf(false) }
    prelude: val padding = PaddingValues(16.dp)
    -->
        “prelude”：把若干行注入到包装体最前面，用来补齐片段里引用到的局部变量
        （例如上面例子里的 liked / padding），从而不必改动章节正文。body 模式
        注入到包装函数体开头，decls 模式则作为包装 object 的成员。

另外：4 个及以上反引号的围栏视为“嵌套引用块”（README 用它包裹示例文本），
里面的 ```kotlin 不会被当成真片段。

生成物
------
* snippets/src/main/kotlin/generated/Ch<章号>_<序号>.kt —— 每个片段一个文件，
  片段被包装进 `internal object Ch<章号>_<序号>`，因此同章多片段不会顶层重名。
  片段里自己写的 `import` 会被提到文件顶层（object / 函数体里不允许 import）。
  该目录整体重新生成（幂等），已加入 .gitignore，不入库。
* snippets/generated_manifest.md —— 清单：章号、行号、片段首行、生成文件。
"""

from __future__ import annotations

import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
MD_GLOB = "*.md"
OUT_DIR = ROOT / "snippets" / "src" / "main" / "kotlin" / "generated"
MANIFEST = ROOT / "snippets" / "generated_manifest.md"

FENCE_OPEN = re.compile(r"^```(\S*)\s*(.*)$")
# 片段里自己写的 `import xxx.yyy`：不能留在函数体 / object 体里，会被提到文件顶层。
IMPORT_LINE = re.compile(r"^import\s+[\w.]+(\.\*)?$")
# 统一给“节选片段”授权的实验 API opt-in。--no-file-optin 下会被换成一行注释，
# 于是编译器报错会直接指出哪些片段依赖实验 API（见文件头的“用法”）。
FILE_OPT_IN = (
    "@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, "
    "androidx.compose.foundation.ExperimentalFoundationApi::class, "
    "androidx.compose.foundation.layout.ExperimentalLayoutApi::class, "
    "androidx.compose.ui.ExperimentalComposeUiApi::class)"
)
GRANT_FILE_OPT_IN = True  # 由 --no-file-optin 置为 False
# 生成文件的固定 import / 头。示例片段被当作“节选”，作者不必在片段里写 import，
# 这里用通配 import 覆盖常用 Compose 包；若新标记的片段用到其它包，在此追加即可。
#
# 注意：这里的 @file:OptIn 是为“节选片段”统一授权的，它只保证片段能编译，
# **不能证明章节正文说明了需要 opt-in**；所以这里不再全局 @file:Suppress("DEPRECATION")，
# 弃用 API 交给独立的严格校验发现：
#   cd snippets && ./gradlew compileDebugKotlin -PsnippetsStrictWarnings --rerun-tasks
# 生成文件的固定 import / 头。示例片段被当作“节选”，作者不必在片段里写 import，
# 这里用通配 import 覆盖常用 Compose 包；若新标记的片段用到其它包，在此追加即可。
#
# 注意：FILE_OPT_IN 是为“节选片段”统一授权的，它只保证片段能编译，
# **不能证明章节正文说明了需要 opt-in**；用 --no-file-optin 可以审计这一点。
# 同时这里没有全局 @file:Suppress("DEPRECATION")，弃用 API 交给独立的严格校验发现：
#   cd snippets && ./gradlew compileDebugKotlin -PsnippetsStrictWarnings --rerun-tasks
IMPORT_HEADER = """\
// 由 tools/extract_snippets.py 从 Markdown 自动生成，请勿手改。
// 来源：{source}:{line}
@file:Suppress("unused", "UNUSED_VARIABLE", "UNUSED_EXPRESSION", "UNUSED_PARAMETER", "UNUSED_ANONYMOUS_PARAMETER", "RedundantSuppression")
{file_opt_in}
package snippets.generated

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import androidx.navigation3.runtime.*
import androidx.navigation3.ui.*
import kotlinx.serialization.*
import snippets.support.*
import kotlinx.coroutines.*
import kotlin.math.*
"""


class Snippet:
    def __init__(self, path: pathlib.Path, start_line: int, body: list[str], mode: str, prelude: list[str]):
        self.path = path
        self.start_line = start_line  # 代码块内首行的行号（1 起，便于回溯）
        self.body = body
        self.mode = mode
        self.prelude = prelude
        self.chapter = chapter_id(path.name)
        self.index = 0  # 同章序号，稍后填充
        self.out_name = ""

    @property
    def first_line(self) -> str:
        for line in self.body:
            if line.strip():
                return line.strip()
        return "(空片段)"


def chapter_id(filename: str) -> str:
    """`26B_Checkbox、Switch与Slider选择控件.md` -> `26B`。"""
    prefix = filename.split("_", 1)[0]
    return re.sub(r"[^0-9A-Za-z]", "", prefix) or "00"


def parse_directive(comment: str) -> tuple[str, list[str]] | None:
    """解析 `<!-- compile ... -->`，返回 (mode, prelude)；不是 compile 标记则返回 None。"""
    text = comment.strip()
    if not (text.startswith("<!--") and text.endswith("-->")):
        return None
    inner = text[4:-3].strip()
    if inner != "compile" and not inner.startswith("compile"):
        return None
    rest = inner[len("compile"):].strip()
    mode = "body"
    prelude: list[str] = []
    for piece in rest.splitlines():
        piece = piece.strip()
        if not piece:
            continue
        if piece.startswith("prelude:"):
            prelude.append(piece[len("prelude:"):].strip())
        elif piece == "decls":
            mode = "decls"
        elif piece == "body":
            mode = "body"
        else:
            raise SystemExit(f"无法识别的 compile 标记内容：{piece!r}")
    return mode, prelude


def collect() -> list[Snippet]:
    snippets: list[Snippet] = []
    for md in sorted(ROOT.glob(MD_GLOB)):
        lines = md.read_text(encoding="utf-8").split("\n")
        pending: tuple[str, list[str]] | None = None
        i = 0
        while i < len(lines):
            line = lines[i]
            stripped = line.strip()

            if stripped.startswith("<!--"):
                # 标记允许跨多行，先把整段注释收集起来。
                comment_lines = [line]
                while "-->" not in comment_lines[-1] and i + 1 < len(lines):
                    i += 1
                    comment_lines.append(lines[i])
                directive = parse_directive("\n".join(comment_lines))
                if directive is not None:
                    pending = directive
                i += 1
                continue

            # 4 个及以上反引号是“嵌套引用块”（README 用它包裹 markdown 示例），
            # 里面的 ```kotlin 只是被展示的文本，不当作真片段。
            nested = re.match(r"^(`{4,})(.*)$", line)
            if nested:
                ticks = len(nested.group(1))
                closing = re.compile(r"^`{%d,}\s*$" % ticks)
                i += 1
                while i < len(lines) and not closing.match(lines[i]):
                    i += 1
                i += 1
                pending = None
                continue

            m = FENCE_OPEN.match(line)
            if m and m.group(1) in ("kotlin", "kt"):
                info = m.group(2).strip()
                mode, prelude = pending if pending else ("body", [])
                if "compile" in info.split():
                    mode = "decls" if "decls" in info.split() else "body"
                    prelude = []
                marked = pending is not None or "compile" in info.split()
                pending = None
                body: list[str] = []
                closed = False
                start = i + 2  # 代码块首行在 md 里的行号（1 起）
                while i + 1 < len(lines):
                    i += 1
                    if lines[i].startswith("```"):
                        closed = True
                        break
                    body.append(lines[i])
                if not closed:
                    raise SystemExit(f"{md.name}:{start} 代码块没有闭合的 ```")
                if marked:
                    if not body:
                        print(f"跳过空代码块 {md.name}:{start}")
                    else:
                        snippets.append(Snippet(md, start, body, mode, prelude))
                i += 1
                continue

            if stripped:
                pending = None  # 标记必须紧邻代码块，中间隔了别的内容就作废
            i += 1
    return snippets


def render(snippet: Snippet) -> str:
    out = IMPORT_HEADER.format(
        source=snippet.path.name,
        line=snippet.start_line,
        file_opt_in=FILE_OPT_IN
        if GRANT_FILE_OPT_IN
        else "// @file:OptIn(...) 已由 --no-file-optin 移除：谁编译不过，谁就依赖实验 API",
    )
    # 片段自己写的 import 提到文件顶层（object / 函数体里不允许 import）。
    imports = sorted({line.strip() for line in snippet.body if IMPORT_LINE.match(line.strip())})
    if imports:
        out += "\n" + "\n".join(imports) + "\n"
    body = [line for line in snippet.body if not IMPORT_LINE.match(line.strip())]
    while body and not body[0].strip():
        body.pop(0)  # 去掉被提走的 import 留下的空行
    out += f"\ninternal object {snippet.out_name[:-3]} {{\n"
    if snippet.mode == "decls":
        for line in snippet.prelude:
            out += indent(line, 4) + "\n"
        if snippet.prelude:
            out += "\n"
        out += "\n".join(indent(line, 4) for line in body) + "\n"
    else:
        out += "    @Composable\n    fun content() {\n"
        for line in snippet.prelude:
            out += indent(line, 8) + "\n"
        if snippet.prelude:
            out += "\n"
        out += "\n".join((indent(line, 8) if line.strip() else "") for line in body) + "\n"
        out += "    }\n"
    out += "}\n"
    return out


def indent(line: str, spaces: int) -> str:
    return " " * spaces + line if line.strip() else ""


def main(args: list[str]) -> int:
    global GRANT_FILE_OPT_IN
    unknown = [a for a in args if a != "--no-file-optin"]
    if unknown:
        raise SystemExit(f"无法识别的参数：{unknown}（只支持 --no-file-optin）")
    GRANT_FILE_OPT_IN = "--no-file-optin" not in args
    if not GRANT_FILE_OPT_IN:
        print("审计模式：不代片段声明 @file:OptIn(...)，编译报错会指出依赖实验 API 的片段")

    snippets = collect()

    counters: dict[str, int] = {}
    for snippet in snippets:
        counters[snippet.chapter] = counters.get(snippet.chapter, 0) + 1
        snippet.index = counters[snippet.chapter]
        snippet.out_name = f"Ch{snippet.chapter}_{snippet.index:02d}.kt"

    if OUT_DIR.exists():
        for old in OUT_DIR.glob("*.kt"):
            old.unlink()
    OUT_DIR.mkdir(parents=True, exist_ok=True)

    for snippet in snippets:
        (OUT_DIR / snippet.out_name).write_text(render(snippet), encoding="utf-8")

    write_manifest(snippets)

    print(f"{len(snippets)} 个片段待校验" if snippets else "0 个片段待校验")
    for snippet in snippets:
        print(f"  {snippet.out_name}  <-  {snippet.path.name}:{snippet.start_line}  ({snippet.mode})")
    print(f"生成目录：{OUT_DIR.relative_to(ROOT)}")
    print(f"清单文件：{MANIFEST.relative_to(ROOT)}")
    return 0


def write_manifest(snippets: list[Snippet]) -> None:
    lines = [
        "# 代码示例编译校验清单（自动生成）",
        "",
        "由 `python3 tools/extract_snippets.py` 从各章 `*.md` 中抽取生成，请勿手改。",
        "",
        "标记方法见 `README.md` 的「代码示例编译校验」一节。",
        "",
        "注意：生成文件会为「节选片段」统一加上 `@file:OptIn(...)`，它只保证片段能编译，",
        "**不代表章节正文已说明需要 opt-in**；弃用 API 也不会在普通编译里报错，",
        "由独立严格编译发现：`./gradlew compileDebugKotlin -PsnippetsStrictWarnings --rerun-tasks`。",
        "",
    ]
    if not snippets:
        lines.append("当前没有标记任何代码块，**0 个片段待校验**。")
    else:
        lines += [
            f"共 {len(snippets)} 个片段待校验。",
            "",
            "| 生成文件 | 章号 | 来源（文件:行号） | 模式 | 片段首行 |",
            "| --- | --- | --- | --- | --- |",
        ]
        for snippet in snippets:
            first = snippet.first_line.replace("|", "\\|")
            if len(first) > 80:
                first = first[:77] + "..."
            lines.append(
                f"| `{snippet.out_name}` | Ch{snippet.chapter} | "
                f"`{snippet.path.name}:{snippet.start_line}` | {snippet.mode} | `{first}` |"
            )
        lines.append("")
    MANIFEST.write_text("\n".join(lines) + "\n", encoding="utf-8")


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
