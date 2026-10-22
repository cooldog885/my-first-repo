#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
配置管理工具
"""

import os
import json
from pathlib import Path

class ConfigManager:
    """配置管理类"""
    
    def __init__(self):
        # 配置文件路径（保存在用户主目录下）
        self.config_dir = Path.home() / '.bbs_image_crawler'
        self.config_file = self.config_dir / 'config.json'
        
        # 默认配置
        self.default_config = {
            'last_save_path': '',
            'window_geometry': {
                'x': 100,
                'y': 100,
                'width': 1000,
                'height': 700
            },
            'download_delay': 0.5,
            'timeout': 15,
            'crawl_images': True,   # 是否抓取图片
            'crawl_text': False,    # 是否抓取文字
            'crawl_mode': 'single', # 页面模式: single(单页) / list(列表页自动遍历)
            'link_pattern': '',     # 帖子链接匹配正则（可选）
            'max_list_pages': 10,   # 列表页模式最多翻页页数（含首页）
            'max_threads': 1000,    # 列表页模式最多抓取帖子数（0=不限）
            'loose_mode': False     # 列表页模式是否使用宽松链接识别
        }
        
        # 确保配置目录存在
        self.config_dir.mkdir(exist_ok=True)
        
        # 加载配置
        self.config = self.load_config()
    
    def load_config(self):
        """
        加载配置文件
        
        Returns:
            dict: 配置字典
        """
        try:
            if self.config_file.exists():
                with open(self.config_file, 'r', encoding='utf-8') as f:
                    config = json.load(f)
                
                # 合并默认配置（确保新增的配置项有默认值）
                merged_config = self.default_config.copy()
                merged_config.update(config)
                return merged_config
            else:
                # 如果配置文件不存在，返回默认配置
                return self.default_config.copy()
                
        except Exception as e:
            print(f"加载配置文件失败: {e}")
            return self.default_config.copy()
    
    def save_config(self):
        """
        保存配置到文件
        
        Returns:
            bool: 是否保存成功
        """
        try:
            with open(self.config_file, 'w', encoding='utf-8') as f:
                json.dump(self.config, f, ensure_ascii=False, indent=2)
            return True
        except Exception as e:
            print(f"保存配置文件失败: {e}")
            return False
    
    def get_last_save_path(self):
        """
        获取上次保存路径
        
        Returns:
            str: 上次保存的路径，如果不存在或无效则返回空字符串
        """
        last_path = self.config.get('last_save_path', '')
        
        # 检查路径是否仍然有效
        if last_path and os.path.exists(last_path) and os.path.isdir(last_path):
            return last_path
        else:
            return ''
    
    def set_last_save_path(self, path):
        """
        设置上次保存路径
        
        Args:
            path: 保存路径
        """
        if path and os.path.exists(path) and os.path.isdir(path):
            self.config['last_save_path'] = path
            self.save_config()
    
    def get_window_geometry(self):
        """
        获取窗口几何信息
        
        Returns:
            dict: 窗口位置和大小信息
        """
        return self.config.get('window_geometry', self.default_config['window_geometry'])
    
    def set_window_geometry(self, x, y, width, height):
        """
        设置窗口几何信息
        
        Args:
            x: 窗口x坐标
            y: 窗口y坐标
            width: 窗口宽度
            height: 窗口高度
        """
        self.config['window_geometry'] = {
            'x': x,
            'y': y,
            'width': width,
            'height': height
        }
        self.save_config()
    
    def get_download_delay(self):
        """
        获取下载延时
        
        Returns:
            float: 下载延时（秒）
        """
        return self.config.get('download_delay', 0.5)
    
    def set_download_delay(self, delay):
        """
        设置下载延时
        
        Args:
            delay: 延时时间（秒）
        """
        self.config['download_delay'] = delay
        self.save_config()
    
    def get_timeout(self):
        """
        获取网络超时时间
        
        Returns:
            int: 超时时间（秒）
        """
        return self.config.get('timeout', 15)
    
    def set_timeout(self, timeout):
        """
        设置网络超时时间
        
        Args:
            timeout: 超时时间（秒）
        """
        self.config['timeout'] = timeout
        self.save_config()
    
    def get_crawl_images(self):
        """
        获取是否抓取图片
        
        Returns:
            bool: 是否抓取图片
        """
        return self.config.get('crawl_images', True)
    
    def set_crawl_images(self, enabled):
        """
        设置是否抓取图片
        
        Args:
            enabled: 是否抓取
        """
        self.config['crawl_images'] = bool(enabled)
        self.save_config()
    
    def get_crawl_text(self):
        """
        获取是否抓取文字
        
        Returns:
            bool: 是否抓取文字
        """
        return self.config.get('crawl_text', False)
    
    def set_crawl_text(self, enabled):
        """
        设置是否抓取文字
        
        Args:
            enabled: 是否抓取
        """
        self.config['crawl_text'] = bool(enabled)
        self.save_config()
    
    def get_crawl_mode(self):
        """
        获取页面模式
        
        Returns:
            str: 'single' 或 'list'
        """
        return self.config.get('crawl_mode', 'single')
    
    def set_crawl_mode(self, mode):
        """
        设置页面模式
        
        Args:
            mode: 'single' 或 'list'
        """
        self.config['crawl_mode'] = mode
        self.save_config()
    
    def get_link_pattern(self):
        """
        获取帖子链接匹配正则
        
        Returns:
            str: 正则字符串
        """
        return self.config.get('link_pattern', '')
    
    def set_link_pattern(self, pattern):
        """
        设置帖子链接匹配正则
        
        Args:
            pattern: 正则字符串
        """
        self.config['link_pattern'] = pattern or ''
        self.save_config()
    
    def get_max_list_pages(self):
        """
        获取最大翻页页数
        
        Returns:
            int: 最多翻页页数
        """
        try:
            return int(self.config.get('max_list_pages', 10))
        except (ValueError, TypeError):
            return 10
    
    def set_max_list_pages(self, pages):
        """
        设置最大翻页页数
        
        Args:
            pages: 页数
        """
        try:
            self.config['max_list_pages'] = max(0, int(pages))
        except (ValueError, TypeError):
            self.config['max_list_pages'] = 10
        self.save_config()
    
    def get_max_threads(self):
        """
        获取最多抓取帖子数
        
        Returns:
            int: 最多帖子数，0 表示不限
        """
        try:
            return int(self.config.get('max_threads', 1000))
        except (ValueError, TypeError):
            return 1000
    
    def set_max_threads(self, count):
        """
        设置最多抓取帖子数
        
        Args:
            count: 帖子数，0 表示不限
        """
        try:
            self.config['max_threads'] = max(0, int(count))
        except (ValueError, TypeError):
            self.config['max_threads'] = 1000
        self.save_config()
    
    def get_loose_mode(self):
        """
        获取是否使用宽松链接识别
        
        Returns:
            bool
        """
        return bool(self.config.get('loose_mode', False))
    
    def set_loose_mode(self, enabled):
        """
        设置是否使用宽松链接识别
        
        Args:
            enabled: 是否启用
        """
        self.config['loose_mode'] = bool(enabled)
        self.save_config()
    
    def reset_config(self):
        """
        重置配置为默认值
        """
        self.config = self.default_config.copy()
        self.save_config()
    
    def get_config_file_path(self):
        """
        获取配置文件路径
        
        Returns:
            str: 配置文件完整路径
        """
        return str(self.config_file) 