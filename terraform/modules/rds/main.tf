# RDS Module - MySQL with Secrets Manager

# Random password for RDS
resource "random_password" "db_password" {
  length  = 32
  special = true
}

# Secrets Manager Secret for RDS password
resource "aws_secretsmanager_secret" "db_password" {
  name        = "${var.project_name}/rds-password"
  description = "RDS MySQL master password"

  tags = merge(
    var.tags,
    {
      Name = "${var.project_name}-rds-password"
    }
  )
}

resource "aws_secretsmanager_secret_version" "db_password" {
  secret_id     = aws_secretsmanager_secret.db_password.id
  secret_string = random_password.db_password.result
}

# DB Subnet Group
resource "aws_db_subnet_group" "main" {
  name       = "${var.project_name}-db-subnet-group"
  subnet_ids = var.private_subnet_ids

  tags = merge(
    var.tags,
    {
      Name = "${var.project_name}-db-subnet-group"
    }
  )
}

# RDS MySQL Instance
resource "aws_db_instance" "main" {
  identifier = "${var.project_name}-mysql"

  engine         = "mysql"
  engine_version = "8.0.39"
  instance_class = var.instance_class

  allocated_storage     = var.allocated_storage
  max_allocated_storage = var.max_allocated_storage
  storage_type          = "gp3"
  storage_encrypted     = true

  db_name  = "unisync" # Initial database name (not used, apps create their own)
  username = var.master_username
  password = random_password.db_password.result

  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = var.security_group_ids

  # Multi-AZ for high availability
  multi_az = var.multi_az

  # Backup configuration
  backup_retention_period = var.backup_retention_period
  backup_window           = "03:00-04:00"
  maintenance_window      = "mon:04:00-mon:05:00"

  # Performance Insights
  performance_insights_enabled = var.performance_insights_enabled

  # Monitoring
  enabled_cloudwatch_logs_exports = ["error", "general", "slow_query"]

  # Deletion protection
  deletion_protection = var.deletion_protection
  skip_final_snapshot = !var.deletion_protection

  # Final snapshot name (if deletion protection is disabled)
  final_snapshot_identifier = var.deletion_protection ? null : "${var.project_name}-mysql-final-snapshot-${formatdate("YYYY-MM-DD-hhmm", timestamp())}"

  # Public access
  publicly_accessible = false

  # Parameter group (optional, using default)
  # parameter_group_name = aws_db_parameter_group.main.name

  tags = merge(
    var.tags,
    {
      Name = "${var.project_name}-mysql"
    }
  )
}

