# 开发工具

Harness、Script Studio 和 MCP 共用以下 schema。编译和加载返回 job，必须轮询 `script_job` 到终态；只有 `loaded` 和实际 generation 表示应用成功。

## script_status

List scripts, disk hashes, running hashes and generations. Does not imply new source is running.

```json
{
  "type": "object",
  "properties": {},
  "required": [],
  "additionalProperties": false
}
```

## script_read

Read one Java source fragment and its expectedHash for conflict-free writes.

```json
{
  "type": "object",
  "properties": {
    "id": {
      "type": "string",
      "description": ""
    }
  },
  "required": [
    "id"
  ],
  "additionalProperties": false
}
```

## script_write

Save a Java source fragment. Does NOT load/reload it. expectedHash must match the read hash, or empty for a new file.

```json
{
  "type": "object",
  "properties": {
    "id": {
      "type": "string",
      "description": ""
    },
    "source": {
      "type": "string",
      "description": ""
    },
    "expectedHash": {
      "type": "string",
      "description": ""
    }
  },
  "required": [
    "id",
    "source",
    "expectedHash"
  ],
  "additionalProperties": false
}
```

## script_delete

Delete source only; does not unload a running generation.

```json
{
  "type": "object",
  "properties": {
    "id": {
      "type": "string",
      "description": ""
    },
    "expectedHash": {
      "type": "string",
      "description": ""
    }
  },
  "required": [
    "id",
    "expectedHash"
  ],
  "additionalProperties": false
}
```

## script_templates

List built-in compilable examples and templates.

```json
{
  "type": "object",
  "properties": {},
  "required": [],
  "additionalProperties": false
}
```

## script_create

Create a new script from a template, without loading it.

```json
{
  "type": "object",
  "properties": {
    "id": {
      "type": "string",
      "description": ""
    },
    "template": {
      "type": "string",
      "description": ""
    }
  },
  "required": [
    "id",
    "template"
  ],
  "additionalProperties": false
}
```

## script_action

Manually check, load, reload or unload a script. Compilation returns a job; poll script_job and inspect state/diagnostics.

```json
{
  "type": "object",
  "properties": {
    "id": {
      "type": "string",
      "description": ""
    },
    "action": {
      "type": "string",
      "enum": [
        "check",
        "load",
        "reload",
        "unload"
      ]
    }
  },
  "required": [
    "id",
    "action"
  ],
  "additionalProperties": false
}
```

## script_job

Read background compilation/application status. loaded identifies an actually applied generation.

```json
{
  "type": "object",
  "properties": {
    "jobId": {
      "type": "string",
      "description": ""
    }
  },
  "required": [
    "jobId"
  ],
  "additionalProperties": false
}
```

## script_cancel

Cancel a queued/compiling job before application. Cannot undo already applied actions.

```json
{
  "type": "object",
  "properties": {
    "jobId": {
      "type": "string",
      "description": ""
    }
  },
  "required": [
    "jobId"
  ],
  "additionalProperties": false
}
```

## script_logs

Read bounded structured script logs using a non-consuming cursor.

```json
{
  "type": "object",
  "properties": {
    "id": {
      "type": "string",
      "description": ""
    },
    "after": {
      "type": "integer",
      "minimum": 0
    },
    "limit": {
      "type": "integer",
      "minimum": 1,
      "maximum": 200
    }
  },
  "required": [],
  "additionalProperties": false
}
```

## script_api

Search actual API declarations, events, utilities and mode hooks. Read results before writing scripts.

```json
{
  "type": "object",
  "properties": {
    "query": {
      "type": "string",
      "description": ""
    },
    "limit": {
      "type": "integer",
      "minimum": 1,
      "maximum": 100
    }
  },
  "required": [],
  "additionalProperties": false
}
```

## script_reference

Read one bundled Markdown/JSON reference. Call without path for the documentation index.

```json
{
  "type": "object",
  "properties": {
    "path": {
      "type": "string",
      "description": ""
    }
  },
  "required": [],
  "additionalProperties": false
}
```

## script_evaluate

Compile and execute a temporary Java method body on the client thread; must return an Object (or null). Trusted code; side effects are real. Poll script_job.

```json
{
  "type": "object",
  "properties": {
    "source": {
      "type": "string",
      "description": ""
    }
  },
  "required": [
    "source"
  ],
  "additionalProperties": false
}
```

## script_screenshot

Capture the current game frame for visual verification. Returns a PNG path and base64 image.

```json
{
  "type": "object",
  "properties": {},
  "required": [],
  "additionalProperties": false
}
```