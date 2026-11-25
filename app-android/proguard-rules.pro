# myco release 混淆规则
# kotlinx.serialization / Compose / AndroidX 均自带 consumer rules；
# 这里只保留显式序列化器不被裁剪（本项目用生成式序列化，通常不需要额外规则）。
-keepattributes *Annotation*, InnerClasses, EnclosingMethod
