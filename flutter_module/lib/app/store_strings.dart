import 'package:flutter/widgets.dart';

class StoreStrings {
  const StoreStrings(this.isChinese);
  final bool isChinese;

  static StoreStrings of(BuildContext context) =>
      StoreStrings(Localizations.localeOf(context).languageCode == 'zh');

  String get title => isChinese ? '模块商店' : 'Module Store';
  String get subtitle =>
      isChinese ? 'Flutter-first 多运行时模块中心' : 'Flutter-first multi-runtime hub';
  String get retry => isChinese ? '重试' : 'Retry';
  String get refresh => isChinese ? '刷新目录' : 'Refresh catalog';
  String get legacy => isChinese ? '打开旧版商店' : 'Open legacy store';
  String get searchHint =>
      isChinese ? '搜索模块、功能或标签' : 'Search modules, features, or tags';
  String get installed => isChinese ? '已安装' : 'Installed';
  String get updates => isChinese ? '可更新' : 'Updates';
  String get downloads => isChinese ? '下载任务' : 'Downloads';
  String get all => isChinese ? '全部' : 'All';
  String get filters => isChinese ? '筛选' : 'Filters';
  String get clear => isChinese ? '清除' : 'Clear';
  String get empty =>
      isChinese ? '没有符合条件的模块' : 'No modules match these filters';
  String get offline =>
      isChinese ? '当前展示本地缓存目录' : 'Showing the local cached catalog';
  String get details => isChinese ? '模块详情' : 'Module details';
  String get install => isChinese ? '安装' : 'Install';
  String get update => isChinese ? '更新' : 'Update';
  String get open => isChinese ? '打开' : 'Open';
  String get uninstall => isChinese ? '卸载' : 'Uninstall';
  String get enable => isChinese ? '启用' : 'Enable';
  String get disable => isChinese ? '禁用' : 'Disable';
  String get rollback => isChinese ? '回滚' : 'Rollback';
  String get permissions => isChinese ? '权限说明' : 'Permissions';
  String get dependencies => isChinese ? '依赖模块' : 'Dependencies';
  String get changelog => isChinese ? '更新日志' : 'Changelog';
  String get updateAll => isChinese ? '一键更新' : 'Update all';
  String get noTasks => isChinese ? '当前没有下载任务' : 'There are no download tasks';
  String get operationFailed => isChinese ? '操作失败' : 'Operation failed';
  String get confirmUninstall => isChinese
      ? '确认卸载此模块？模块数据将按原生策略保留。'
      : 'Uninstall this module? Module data follows the native retention policy.';
  String get cancel => isChinese ? '取消' : 'Cancel';
  String get confirm => isChinese ? '确认' : 'Confirm';
}
