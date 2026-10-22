#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
文字爬虫核心功能

从网页中提取可读正文内容，并保存为 Markdown 文件。
复用与图片爬虫相同的会话/重试/SSL 处理逻辑，保证兼容性。
"""

import os
import re
import time
import requests
from urllib.parse import urljoin, urlparse
from bs4 import BeautifulSoup
import ssl
import urllib3
from requests.adapters import HTTPAdapter
from urllib3.util.retry import Retry

# 禁用SSL警告
urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)


class TextCrawler:
    """文字爬虫类"""

    def __init__(self):
        self.session = requests.Session()

        # 配置重试策略
        retry_strategy = Retry(
            total=3,
            backoff_factor=1,
            status_forcelist=[429, 500, 502, 503, 504],
        )

        adapter = HTTPAdapter(max_retries=retry_strategy)
        self.session.mount("http://", adapter)
        self.session.mount("https://", adapter)

        # 设置请求头，模拟浏览器
        self.session.headers.update({
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36',
            'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8',
            'Accept-Language': 'zh-CN,zh;q=0.8,en-US;q=0.5,en;q=0.3',
            'Accept-Encoding': 'gzip, deflate',
            'Connection': 'keep-alive',
            'Upgrade-Insecure-Requests': '1',
        })

    def crawl_text(self, url, save_path, progress_callback=None):
        """
        抓取网页正文文字

        Args:
            url: 目标网址
            save_path: 基础保存路径
            progress_callback: 进度回调函数

        Returns:
            str: 保存的 Markdown 文件路径；失败抛出异常
        """
        try:
            if progress_callback:
                progress_callback("正在获取网页内容...")

            response = self._safe_request(url)

            if progress_callback:
                progress_callback("正在解析网页正文...")

            soup = BeautifulSoup(response.text, 'html.parser')

            # 移除无关注释/脚本/样式等标签
            for tag in soup(['script', 'style', 'noscript', 'header', 'footer',
                             'nav', 'iframe', 'svg', 'form', 'button', 'aside']):
                tag.decompose()

            title = self._extract_title(soup, url)

            # 定位正文主容器
            content_root = self._find_main_content(soup)

            if progress_callback:
                progress_callback("正在提取正文文字...")

            blocks = self._extract_text_blocks(content_root)

            if not blocks:
                if progress_callback:
                    progress_callback("未提取到有效正文内容")
                raise Exception("未提取到有效正文内容")

            # 生成保存目录（与图片相同的命名规则）
            folder_name = self._generate_folder_name(soup, url, title)
            save_dir = os.path.join(save_path, folder_name)
            os.makedirs(save_dir, exist_ok=True)

            # 生成文件名
            filename = self._clean_filename(title) + '.md'
            file_path = os.path.join(save_dir, filename)

            # 避免覆盖：若已存在则追加序号
            file_path = self._ensure_unique_path(file_path)

            markdown = self._build_markdown(title, url, blocks)
            with open(file_path, 'w', encoding='utf-8') as f:
                f.write(markdown)

            char_count = len(markdown)
            if progress_callback:
                progress_callback(
                    f"文字提取完成，共 {char_count} 字符，已保存: {file_path}"
                )

            return file_path

        except Exception as e:
            raise Exception(f"文字抓取失败: {str(e)}")

    # ------------------------------------------------------------------
    # 列表页自动遍历相关
    # ------------------------------------------------------------------
    def discover_thread_links(self, url, max_threads=50, max_list_pages=10,
                               pattern=None, loose=False, progress_callback=None):
        """
        从列表/版块页中发现帖子链接，并自动跟随「下一页」翻页

        Args:
            url: 起始列表页 URL
            max_threads: 最多抓取的帖子数量（0 或负数表示不限）
            max_list_pages: 最多翻页页数（含起始页）；0 或负数表示不限页数
            pattern: 可选的正则表达式，href 需匹配才算帖子链接
            progress_callback: 进度回调

        Returns:
            list: 帖子绝对链接列表（已去重，按发现顺序）
        """
        compiled = None
        if pattern:
            try:
                compiled = re.compile(pattern, re.IGNORECASE)
            except re.error:
                compiled = None

        base_domain = urlparse(url).netloc
        visited_list = set()
        list_queue = [url]
        thread_links = []
        list_page_count = 0

        while list_queue and (max_list_pages is None or max_list_pages <= 0
                               or list_page_count < max_list_pages):
            list_url = list_queue.pop(0)
            if list_url in visited_list:
                continue
            visited_list.add(list_url)
            list_page_count += 1

            if progress_callback:
                if max_list_pages and max_list_pages > 0:
                    progress_callback(
                        f"扫描列表页 [{list_page_count}/{max_list_pages}]: {list_url}"
                    )
                else:
                    progress_callback(f"扫描列表页 [{list_page_count}]: {list_url}")

            try:
                response = self._safe_request(list_url)
            except Exception as e:
                if progress_callback:
                    progress_callback(f"列表页请求失败: {e}")
                continue

            soup = BeautifulSoup(response.text, 'html.parser')
            seen = set(thread_links)

            threads_limit = max_threads if (max_threads and max_threads > 0) else None

            for a in soup.find_all('a', href=True):
                if threads_limit is not None and len(thread_links) >= threads_limit:
                    break
                href = a['href'].strip()
                if not href:
                    continue
                if href.startswith(('#', 'javascript:', 'mailto:', 'tel:')):
                    continue

                abs_url = urljoin(list_url, href)
                parsed = urlparse(abs_url)
                if parsed.netloc != base_domain:
                    continue
                if abs_url.rstrip('/') == list_url.rstrip('/'):
                    continue
                if abs_url in seen:
                    continue

                # 宽松模式：仅排除静态资源与纯翻页/导航链接；正常模式：按规则过滤
                if loose:
                    if not self._is_loose_candidate(abs_url, a):
                        continue
                else:
                    if not self._is_thread_link(abs_url, a):
                        continue

                # 若提供了正则规则，href 必须匹配
                if compiled and not compiled.search(abs_url):
                    continue

                seen.add(abs_url)
                thread_links.append(abs_url)

            if threads_limit is not None and len(thread_links) >= threads_limit:
                break

            # 发现「下一页」并加入队列继续翻页
            next_url = self._find_next_page(soup, list_url, base_domain)
            if next_url and next_url not in visited_list:
                list_queue.append(next_url)

        return thread_links

    def _find_next_page(self, soup, current_url, base_domain):
        """
        在列表页中寻找「下一页」链接

        依次尝试：文字明确为下一页/next 的链接、带 next 语义 class 的链接。
        """
        next_texts = ['下一页', '下页', '下一頁', 'next', '»', '›', '>']
        prev_texts = ['上一页', '上页', '上一頁', 'prev', '«', '‹', '<']

        # 1) 文字匹配的下一页链接（包含式，兼容"下一页 >"等带附加符号的情况）
        for a in soup.find_all('a', href=True):
            txt = a.get_text(strip=True).lower()
            if ('下一页' in txt or '下页' in txt or '下一頁' in txt
                    or txt in ('next', '»', '›', '>')):
                if not any(p in txt for p in prev_texts):
                    href = a['href'].strip()
                    if href and not href.startswith(('#', 'javascript:')):
                        return urljoin(current_url, href)

        # 2) 分页容器中带 next 语义 class 的链接
        for a in soup.find_all('a', href=True):
            cls = ' '.join(a.get('class', [])).lower()
            if 'next' in cls and 'prev' not in cls:
                href = a['href'].strip()
                if href and not href.startswith(('#', 'javascript:')):
                    return urljoin(current_url, href)

        return None

    STATIC_EXT = ('.jpg', '.jpeg', '.png', '.gif', '.bmp', '.webp', '.css',
                  '.js', '.ico', '.svg', '.gif')

    def _is_loose_candidate(self, abs_url, a_tag):
        """
        宽松模式下的候选判断：保留几乎所有同域链接，
        仅排除静态资源与明显的翻页/导航链接（上一页/下一页/纯数字页码）。
        """
        path = abs_url.lower()
        text = a_tag.get_text(strip=True)

        # 排除静态资源
        if any(path.endswith(ext) for ext in self.STATIC_EXT):
            return False

        # 排除纯翻页/导航文字链接
        nav_texts = {'上一页', '上页', '下一页', '下页', '末页', '首页',
                     '上一頁', '下一頁', '尾页', 'next', 'prev', 'last', 'first'}
        if text in nav_texts:
            return False
        if text.isdigit():  # 页码数字（如 2 3 4）
            return False

        return True

    def _is_thread_link(self, abs_url, a_tag):
        """判断一个链接是否像帖子/文章详情链接"""
        path = abs_url.lower()
        text = a_tag.get_text(strip=True)

        # 排除静态资源
        if any(path.endswith(ext) for ext in
               ['.jpg', '.jpeg', '.png', '.gif', '.bmp', '.webp', '.css', '.js', '.ico', '.svg']):
            return False

        # 排除明显的翻页/版块/列表/用户等链接
        exclude_path = ['forum', 'board', 'list', 'category', 'node', 'plate',
                        'login', 'register', 'sign', 'user', 'member', 'profile',
                        'search', 'home', 'index', '?page=', 'page=', '&page=']
        if any(k in path for k in exclude_path):
            return False

        # 帖子链接常见关键词
        include = ['thread', 'topic', 'post', 'tid=', 'fid=', 'read', 'view',
                   'viewtopic', 'viewthread', 'showthread', 'article', 'show',
                   'detail', '?id=', '/t/', '/p/', 'blog', 'news', 'doc', 'item']
        if any(k in path for k in include):
            return True

        # 链接文字较长（像帖子标题），且无版块/列表特征
        board_kw = ['forum', 'board', 'list', 'category', 'node', 'plate']
        if len(text) >= 6 and not any(k in path for k in board_kw):
            return True

        return False

    def crawl_first_post(self, url, save_path, progress_callback=None):
        """
        抓取某帖子的「一楼（主楼）」正文并保存为 Markdown

        Args:
            url: 帖子详情页 URL
            save_path: 基础保存路径
            progress_callback: 进度回调

        Returns:
            str: 保存的 Markdown 文件路径
        """
        try:
            if progress_callback:
                progress_callback("正在获取帖子页面...")

            response = self._safe_request(url)

            if progress_callback:
                progress_callback("正在定位一楼正文...")

            soup = BeautifulSoup(response.text, 'html.parser')

            for tag in soup(['script', 'style', 'noscript', 'header', 'footer',
                             'nav', 'iframe', 'svg', 'form', 'button', 'aside']):
                tag.decompose()

            title = self._extract_title(soup, url)

            # 优先定位一楼（主楼）容器
            post_root = self._find_first_post(soup)
            if post_root is None:
                post_root = self._find_main_content(soup)

            # 一楼正文可能位于 <div> 内（而非 <p>），使用更宽松的块级提取
            blocks = self._extract_post_blocks(post_root)

            if not blocks:
                if progress_callback:
                    progress_callback("未提取到一楼正文")
                raise Exception("未提取到一楼正文")

            folder_name = self._generate_folder_name(soup, url, title)
            save_dir = os.path.join(save_path, folder_name)
            os.makedirs(save_dir, exist_ok=True)

            filename = self._clean_filename(title) + '_一楼.md'
            file_path = os.path.join(save_dir, filename)
            file_path = self._ensure_unique_path(file_path)

            markdown = self._build_markdown(title, url, blocks)
            with open(file_path, 'w', encoding='utf-8') as f:
                f.write(markdown)

            if progress_callback:
                progress_callback(f"一楼文字已保存: {file_path}")

            return file_path

        except Exception as e:
            raise Exception(f"一楼抓取失败: {str(e)}")

    def _find_first_post(self, soup):
        """
        定位帖子的一楼（主楼）内容容器

        策略：只在带 post/article/content/message 等语义化标记、且不含
        reply/comment/quote 等回复标记的容器中，选取文字量最大的「外层」
        容器作为主楼，再进一步定位其中的正文子节点。
        """
        candidates = soup.find_all(['div', 'article', 'section', 'li', 'td'])

        posts = []
        for c in candidates:
            cls = ' '.join(c.get('class', [])).lower()
            cid = (c.get('id') or '').lower()
            combined = f"{cls} {cid}"

            # 排除回复/评论类容器
            if any(k in combined for k in
                   ['reply', 'replay', 'comment', 'quote', 're-', 'floor-2',
                    'floor2', 'li-reply', 'post-reply']):
                continue

            # 必须带主楼语义标记
            if not any(k in combined for k in
                       ['post', 'floor', 'tid', 'article', 'content', 'message',
                        'body', 'text', 'topic', 'thread', 'first', 'main',
                        'blog', 'detail', 'read', 'postmessage']):
                continue

            posts.append(c)

        if not posts:
            return None

        # 按文字量降序，取最大者
        posts.sort(key=lambda x: len(x.get_text(strip=True)), reverse=True)
        best = posts[0]

        # 若 best 被另一个候选包含，则改用更外层容器
        for p in posts:
            if p is not best and best in p.descendants:
                best = p
                break

        # 文字过少则放弃，回退到整页正文
        if len(best.get_text(strip=True)) < 10:
            return None

        # 在容器内进一步定位真正的内容子节点
        inner = self._find_inner_content(best)
        return inner if inner is not None else best

    def _find_inner_content(self, container):
        """在帖子容器内寻找最像正文的子节点"""
        best = None
        best_len = 0
        for child in container.find_all(['div', 'article', 'section', 'p']):
            cls = ' '.join(child.get('class', [])).lower()
            cid = (child.get('id') or '').lower()
            combined = f"{cls} {cid}"
            if any(k in combined for k in
                   ['content', 'message', 'text', 'body', 'postmessage',
                    'post-body', 'article', 'inner', 'detail']):
                length = len(child.get_text(strip=True))
                if length > best_len:
                    best_len = length
                    best = child
        return best

    def _safe_request(self, url, max_retries=3):
        """安全的网络请求，处理SSL错误和重试（与图片爬虫保持一致）"""
        for attempt in range(max_retries):
            try:
                response = self.session.get(url, timeout=15, verify=True)
                response.raise_for_status()
                response.encoding = response.apparent_encoding
                return response

            except requests.exceptions.SSLError as e:
                if attempt < max_retries - 1:
                    try:
                        response = self.session.get(url, timeout=15, verify=False)
                        response.raise_for_status()
                        response.encoding = response.apparent_encoding
                        return response
                    except Exception as e2:
                        if attempt < max_retries - 1:
                            time.sleep(2 ** attempt)
                            continue
                        else:
                            raise e2
                else:
                    raise e

            except requests.exceptions.ConnectionError as e:
                if attempt < max_retries - 1:
                    time.sleep(2 ** attempt)
                    continue
                else:
                    raise e

            except requests.exceptions.Timeout as e:
                if attempt < max_retries - 1:
                    time.sleep(2 ** attempt)
                    continue
                else:
                    raise e

            except Exception as e:
                if attempt < max_retries - 1:
                    time.sleep(2 ** attempt)
                    continue
                else:
                    raise e

    def _extract_title(self, soup, url):
        """提取网页标题"""
        title_tag = soup.find('title')
        if title_tag and title_tag.string:
            return title_tag.string.strip()

        h1_tag = soup.find('h1')
        if h1_tag:
            return h1_tag.get_text().strip()

        domain = urlparse(url).netloc
        return f"text_from_{domain}"

    def _find_main_content(self, soup):
        """
        通过评分定位最可能包含正文内容的主容器

        优先选择带有 content/post/article/body 等语义化 class/id 的标签，
        同时剔除导航、侧边栏、广告等噪声区域。
        """
        candidates = soup.find_all(['article', 'main', 'div', 'section'])
        best = None
        best_score = 0

        for c in candidates:
            score = 0
            text = c.get_text(strip=True)
            score += len(text)

            cls = ' '.join(c.get('class', [])).lower()
            cid = (c.get('id') or '').lower()

            # 正文正向关键词
            for kw in ['content', 'post', 'article', 'topic', 'body', 'text',
                       'reply', 'message', 'comment', 'detail', 'main']:
                if kw in cls or kw in cid:
                    score += 200

            # 噪声负向关键词
            for kw in ['nav', 'side', 'menu', 'header', 'footer',
                       'comment-form', 'widget', 'ad', 'advert', 'sidebar']:
                if kw in cls or kw in cid:
                    score -= 100

            if score > best_score:
                best_score = score
                best = c

        # 评分过低则回退到 body
        if best is None or best_score < 200:
            best = soup.body or soup

        return best

    def _extract_text_blocks(self, root):
        """
        从正文容器中按语义提取文字块

        Returns:
            list: [(标签名, 文本), ...] 按文档顺序
        """
        blocks = []
        target_tags = {'h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'p', 'li',
                       'blockquote', 'pre', 'code', 'td', 'dd', 'dt', 'figcaption'}

        for elem in root.find_all(target_tags):
            # 跳过已被祖先目标标签包含的内容，避免重复
            if any(ancestor.name in target_tags for ancestor in elem.parents):
                continue
            txt = elem.get_text(' ', strip=True)
            if txt:
                blocks.append((elem.name, txt))

        return blocks

    def _extract_post_blocks(self, root):
        """
        从一楼容器中按块级元素提取文字

        相比 _extract_text_blocks 更宽松：额外接受 div/section/article 作为
        块级容器（很多论坛一楼正文直接放在 <div> 中，没有 <p> 包裹），
        并跳过已被祖先块级元素包含的嵌套元素，避免重复。
        """
        blocks = []
        block_tags = {'h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'p', 'li',
                      'blockquote', 'pre', 'code', 'td', 'dd', 'dt',
                      'figcaption', 'div', 'section', 'article'}

        # 将 root 本身也纳入，避免根节点即为正文容器（叶子 div）时漏提
        elements = [root] + list(root.find_all(block_tags))

        for elem in elements:
            # 跳过被其他块级祖先（非 root）包含的元素，避免重复
            if elem is not root and any(
                ancestor.name in block_tags for ancestor in elem.parents
                if ancestor is not root
            ):
                continue

            txt = elem.get_text(' ', strip=True)
            if not txt:
                continue

            # 仅标题/列表/引用/代码保留特殊格式，其余按段落处理
            if elem.name in ('h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'li',
                             'blockquote', 'pre', 'code'):
                blocks.append((elem.name, txt))
            else:
                blocks.append(('p', txt))

        return blocks

    def _build_markdown(self, title, url, blocks):
        """将提取的文字块组装为 Markdown 文本"""
        lines = []
        lines.append(f'# {title}')
        lines.append('')
        lines.append(f'> 来源: {url}')
        lines.append('')
        lines.append(f'> 提取时间: {time.strftime("%Y-%m-%d %H:%M:%S")}')
        lines.append('')

        for tag, txt in blocks:
            if tag == 'h1':
                lines.append(f'# {txt}')
            elif tag == 'h2':
                lines.append(f'## {txt}')
            elif tag == 'h3':
                lines.append(f'### {txt}')
            elif tag == 'h4':
                lines.append(f'#### {txt}')
            elif tag == 'h5':
                lines.append(f'##### {txt}')
            elif tag == 'h6':
                lines.append(f'###### {txt}')
            elif tag == 'li':
                lines.append(f'- {txt}')
            elif tag == 'blockquote':
                lines.append(f'> {txt}')
            elif tag in ('pre', 'code'):
                lines.append('```')
                lines.append(txt)
                lines.append('```')
            else:
                lines.append(txt)
            lines.append('')

        text = '\n'.join(lines)
        # 压缩多余空行
        text = re.sub(r'\n{3,}', '\n\n', text)
        return text.strip() + '\n'

    def _ensure_unique_path(self, file_path):
        """若文件已存在，则在文件名后追加序号"""
        if not os.path.exists(file_path):
            return file_path

        base, ext = os.path.splitext(file_path)
        index = 1
        while True:
            candidate = f"{base}_{index}{ext}"
            if not os.path.exists(candidate):
                return candidate
            index += 1

    def _generate_folder_name(self, soup, url, title=None):
        """生成文件夹名称（与图片爬虫逻辑保持一致）"""
        if not title:
            title = self._extract_title(soup, url)

        cleaned_title = self._clean_filename(title)

        if len(cleaned_title) < 3:
            domain = urlparse(url).netloc
            timestamp = int(time.time())
            cleaned_title = f"text_{domain}_{timestamp}"
            cleaned_title = self._clean_filename(cleaned_title)

        if len(cleaned_title) > 100:
            cleaned_title = cleaned_title[:100]

        # 添加时间戳避免重复
        timestamp = int(time.time())
        return f"{cleaned_title}_{timestamp}"

    def _clean_filename(self, filename):
        """清理文件名/文件夹名，去掉非法字符"""
        if not filename:
            return "untitled"

        illegal_chars = r'[<>:"/\\|?*]'
        cleaned = re.sub(illegal_chars, '_', filename)
        cleaned = re.sub(r'[\x00-\x1f]', '', cleaned)
        cleaned = re.sub(r'\s+', ' ', cleaned)
        cleaned = cleaned.strip()
        cleaned = cleaned.strip('.')

        windows_reserved = {
            'CON', 'PRN', 'AUX', 'NUL',
            'COM1', 'COM2', 'COM3', 'COM4', 'COM5', 'COM6', 'COM7', 'COM8', 'COM9',
            'LPT1', 'LPT2', 'LPT3', 'LPT4', 'LPT5', 'LPT6', 'LPT7', 'LPT8', 'LPT9'
        }

        if cleaned.upper() in windows_reserved:
            cleaned = f"_{cleaned}_"

        if not cleaned:
            cleaned = "untitled"

        return cleaned
