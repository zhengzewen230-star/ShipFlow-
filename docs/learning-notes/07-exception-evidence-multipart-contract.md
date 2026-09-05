# 异常证据 multipart 契约

- 浏览器端必须把文件、description 和 version 放入 `FormData`；不要手动设置 `Content-Type`，以便 Axios/浏览器生成 multipart boundary。
- 文件使用 `form.append("file", file, file.name)`，服务端使用 `@RequestPart MultipartFile`；version 是表单参数，应由 `@RequestParam Long` 绑定。
- 不支持的媒体类型或缺少 multipart part 必须映射为明确 4xx 与 Trace ID，不能落入通用 500。
- 上传成功后重新读取异常详情，并将返回的 attachmentId 作为当前异常索赔证据引用。
