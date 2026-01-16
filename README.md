# O365 管理系统（Fork & Enhanced）

本项目基于上游二次开发：`https://github.com/vanyouseea/o365`  
用于 Microsoft Graph 的 O365 多全局管理，并提供自助注册入口。

## 相对上游的主要改动
- **Docker 部署 + H2 持久化**
  - 默认使用 H2：`./data/o365`（建议映射到宿主机目录持久化）
- **公开注册（无邀请码）增强**
  - 后台开关、名额上限（0=不限）、已用统计、默认域名、固定订阅配置
  - 域名下拉来自 Graph 已验证域名（显示为 `@xxx`）
  - 固定订阅支持 **多选 SKU**（可同时分配多个订阅）
- **邀请码注册体验优化**
  - 邀请码模式下隐藏域名选择、自动展示邀请码后缀等
- **稳定性与交互修复**
  - 优化 assignLicense 等错误输出与前端提示


---

## Docker 本地构建（推荐）
```bash
docker build -t o365:latest .

mkdir -p /root/o365-data
docker rm -f o365 2>/dev/null || true
docker run -d --name o365 \
  -p 9527:9527 \
  -v /root/o365-data:/data \
  --restart unless-stopped \
  o365:latest
```
---
## 预览
- **首页** 
![alt 首页](https://github.com/datout/O365/blob/e68402fd6d2e7b5a39b8c9b97705ae734a4b51dc/pic/_3811.png)
- **新增** 
![alt 邀请注册](https://github.com/datout/O365/blob/e68402fd6d2e7b5a39b8c9b97705ae734a4b51dc/pic/_3812.png)
- **注册页** 
![alt 注册](https://github.com/datout/O365/blob/79aa493a34bcb82811d96dae08754e81e0189e75/pic/_901.png)
## Microsoft Graph 应用权限（Application permissions）

## 为保证全部功能可用，建议授予并执行 Admin consent：

Application.ReadWrite.All

Application.ReadWrite.OwnedBy

Directory.ReadWrite.All

RoleManagement.ReadWrite.Directory

User.ManageIdentities.All

User.ReadWrite.All

Reports.Read.All

Sites.FullControl.All

Domain.ReadWrite.All
