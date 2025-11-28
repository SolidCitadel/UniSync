# Secrets Manager Module

# Canvas Sync API Key Secret
resource "aws_secretsmanager_secret" "canvas_sync_api_key" {
  name        = "${var.project_name}/canvas-sync-api-key"
  description = "Canvas Sync Lambda API key for internal authentication"

  tags = merge(
    var.tags,
    {
      Name = "${var.project_name}-canvas-sync-api-key"
    }
  )
}

resource "aws_secretsmanager_secret_version" "canvas_sync_api_key" {
  secret_id     = aws_secretsmanager_secret.canvas_sync_api_key.id
  secret_string = var.canvas_sync_api_key
}

