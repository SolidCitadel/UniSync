# Main Terraform Configuration for UniSync Infrastructure

terraform {
  required_version = ">= 1.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.1"
    }
  }

  # Local backend (state file stored locally)
  backend "local" {
    path = "terraform.tfstate"
  }
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = var.common_tags
  }
}

# Network Module
module "network" {
  source = "./modules/network"

  vpc_cidr             = var.vpc_cidr
  public_subnet_cidrs  = var.public_subnet_cidrs
  private_subnet_cidrs = var.private_subnet_cidrs
  availability_zones   = var.availability_zones
  project_name         = var.project_name
  tags                 = var.common_tags
}

# Security Groups Module
module "security_groups" {
  source = "./modules/security-groups"

  vpc_id         = module.network.vpc_id
  project_name   = var.project_name
  ec2_cidr_blocks = var.ec2_cidr_blocks
  tags           = var.common_tags
}

# RDS Module
module "rds" {
  source = "./modules/rds"

  project_name        = var.project_name
  private_subnet_ids  = module.network.private_subnet_ids
  security_group_ids  = [module.security_groups.rds_security_group_id]
  instance_class      = var.rds_instance_class
  allocated_storage   = var.rds_allocated_storage
  max_allocated_storage = var.rds_max_allocated_storage
  master_username     = var.rds_master_username
  multi_az            = var.rds_multi_az
  backup_retention_period = var.rds_backup_retention_period
  performance_insights_enabled = var.rds_performance_insights_enabled
  deletion_protection = var.rds_deletion_protection
  tags                = var.common_tags
}

