import 'dart:convert';

/// 模块详情数据模型（#11.1）。
/// 从 NativeModule.detailsJson（JSON 字符串）解析。
class ModuleDetail {
  final String valueDescription;
  final String audience;
  final String offlineCapability;
  final String updateImpact;
  final String uninstallImpact;
  final List<String> highlights;
  final List<String> limitations;

  const ModuleDetail({
    this.valueDescription = '',
    this.audience = '',
    this.offlineCapability = '',
    this.updateImpact = '',
    this.uninstallImpact = '',
    this.highlights = const [],
    this.limitations = const [],
  });

  bool get hasContent =>
      valueDescription.isNotEmpty ||
      audience.isNotEmpty ||
      offlineCapability.isNotEmpty ||
      updateImpact.isNotEmpty ||
      uninstallImpact.isNotEmpty ||
      highlights.isNotEmpty ||
      limitations.isNotEmpty;

  factory ModuleDetail.fromJson(String? jsonStr) {
    if (jsonStr == null || jsonStr.trim().isEmpty) {
      return const ModuleDetail();
    }
    try {
      final json = jsonDecode(jsonStr) as Map<String, dynamic>;
      return ModuleDetail(
        valueDescription: (json['valueDescription'] as String?) ?? '',
        audience: (json['audience'] as String?) ?? '',
        offlineCapability: (json['offlineCapability'] as String?) ?? '',
        updateImpact: (json['updateImpact'] as String?) ?? '',
        uninstallImpact: (json['uninstallImpact'] as String?) ?? '',
        highlights: ((json['highlights'] as List<dynamic>?) ?? const [])
            .whereType<String>()
            .toList(growable: false),
        limitations: ((json['limitations'] as List<dynamic>?) ?? const [])
            .whereType<String>()
            .toList(growable: false),
      );
    } on Object {
      return const ModuleDetail();
    }
  }
}

/// 隐私卡数据模型（#11.2）。
/// 从 NativeModule.privacyJson（JSON 字符串）解析。
class PrivacyCard {
  final String localData;
  final String cloudData;
  final List<String> networkDomains;
  final String syncLocation;
  final String retentionPeriod;
  final String deletionMethod;

  const PrivacyCard({
    this.localData = '',
    this.cloudData = '',
    this.networkDomains = const [],
    this.syncLocation = '',
    this.retentionPeriod = '',
    this.deletionMethod = '',
  });

  bool get hasContent =>
      localData.isNotEmpty ||
      cloudData.isNotEmpty ||
      networkDomains.isNotEmpty ||
      syncLocation.isNotEmpty ||
      retentionPeriod.isNotEmpty ||
      deletionMethod.isNotEmpty;

  bool get involvesCloud =>
      cloudData.isNotEmpty ||
      networkDomains.isNotEmpty ||
      syncLocation.isNotEmpty;

  factory PrivacyCard.fromJson(String? jsonStr) {
    if (jsonStr == null || jsonStr.trim().isEmpty) {
      return const PrivacyCard();
    }
    try {
      final json = jsonDecode(jsonStr) as Map<String, dynamic>;
      return PrivacyCard(
        localData: (json['localData'] as String?) ?? '',
        cloudData: (json['cloudData'] as String?) ?? '',
        networkDomains: ((json['networkDomains'] as List<dynamic>?) ?? const [])
            .whereType<String>()
            .toList(growable: false),
        syncLocation: (json['syncLocation'] as String?) ?? '',
        retentionPeriod: (json['retentionPeriod'] as String?) ?? '',
        deletionMethod: (json['deletionMethod'] as String?) ?? '',
      );
    } on Object {
      return const PrivacyCard();
    }
  }
}
