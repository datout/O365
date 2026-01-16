# Patched by ChatGPT (2026-01-09)

Fixes for license assignment failures in Microsoft Graph `assignLicense` calls.

## What changed
1. Fixed invalid JSON body for `POST /users/{id|upn}/assignLicense`:
   - Removed the trailing comma after `"skuId": "...",` which makes JSON invalid.
   - Normalized the JSON body to:
     `{"addLicenses":[{"disabledPlans":[],"skuId":"<GUID>"}],"removeLicenses":[]}`

2. Improved diagnostics:
   - In `catch (Exception e)` blocks around license assignment, if the exception is a
     `HttpStatusCodeException`, print HTTP status code and Graph error response body
     to stdout (so you can see it in `docker logs`).

3. Defensive validation:
   - Before calling Graph, validate the selected `skuId` looks like a GUID.
   - If invalid:
     - Create user flow: append a message and skip that sku
     - Invite flow: mark invite as failed and return
     - Mass flow: count as failure and continue

## Files modified
- src/main/java/o365/service/CreateOfficeUser.java
- src/main/java/o365/service/CreateOfficeUserByInviteCd.java
- src/main/java/o365/service/MassCreateOfficeUser.java


## Patch Updates (2026-01-15)

- UI: “公开注册设置”页面排版优化（GitHub Settings 风格对齐）：label/说明在左，控件在右，移动端自动纵向排列。
- Feature: 固定订阅（skuId）支持多选（勾选/搜索/已选 chips），后端保存为逗号分隔并在注册时一次性分配全部所选 SKU。
- UI: 首页补全 Graph 应用程序权限清单，并提供复制按钮（便于 Azure Portal 配置与 Admin consent）。
