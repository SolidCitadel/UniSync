# Cognito User Pool Module
# Creates AWS Cognito User Pool and App Client for UniSync authentication

resource "aws_cognito_user_pool" "main" {
  name = "${var.project_name}-user-pool"

  # Password Policy (matching LocalStack configuration)
  password_policy {
    minimum_length    = 8
    require_lowercase = true
    require_uppercase = true
    require_numbers   = true
    require_symbols   = false
  }

  # MFA Configuration
  mfa_configuration = "OFF"

  # Auto-verified attributes
  auto_verified_attributes = ["email"]

  # Username attributes (allow email as username)
  username_attributes = ["email"]

  # User attributes schema
  schema {
    name                = "email"
    attribute_data_type = "String"
    required            = true
    mutable             = false

    string_attribute_constraints {
      min_length = 1
      max_length = 256
    }
  }

  schema {
    name                = "name"
    attribute_data_type = "String"
    required            = true
    mutable             = true

    string_attribute_constraints {
      min_length = 1
      max_length = 256
    }
  }

  # Account recovery
  account_recovery_setting {
    recovery_mechanism {
      name     = "verified_email"
      priority = 1
    }
  }

  # Email configuration (using default SES)
  email_configuration {
    email_sending_account = "COGNITO_DEFAULT"
  }

  # Deletion protection (prevent accidental deletion)
  deletion_protection = "INACTIVE"

  tags = merge(
    var.tags,
    {
      Name = "${var.project_name}-user-pool"
    }
  )
}

# Cognito User Pool Client
resource "aws_cognito_user_pool_client" "main" {
  name         = "${var.project_name}-app-client"
  user_pool_id = aws_cognito_user_pool.main.id

  # No client secret (public client for mobile/web apps)
  generate_secret = false

  # Explicit auth flows
  explicit_auth_flows = [
    "ALLOW_USER_PASSWORD_AUTH",
    "ALLOW_REFRESH_TOKEN_AUTH",
    "ALLOW_USER_SRP_AUTH"
  ]

  # Token validity (using defaults)
  access_token_validity  = 60  # 60 minutes
  id_token_validity      = 60  # 60 minutes
  refresh_token_validity = 30  # 30 days

  token_validity_units {
    access_token  = "minutes"
    id_token      = "minutes"
    refresh_token = "days"
  }

  # Prevent user existence errors
  prevent_user_existence_errors = "ENABLED"

  # Read/write attributes
  read_attributes = [
    "email",
    "email_verified",
    "name"
  ]

  write_attributes = [
    "email",
    "name"
  ]
}
