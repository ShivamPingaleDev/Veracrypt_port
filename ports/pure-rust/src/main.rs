//! CLI for the educational scratch Rust lab.

use std::path::PathBuf;

use clap::{Parser, Subcommand};
use vcport_pure_rust::{encode_hex, list_entries, open_toy, read_file_bytes, sha256, write_toy};

#[derive(Parser)]
#[command(name = "vcedu-rust")]
#[command(about = "Educational scratch Rust lab — not VeraCrypt", long_about = None)]
struct Cli {
    #[command(subcommand)]
    cmd: Commands,
}

#[derive(Subcommand)]
enum Commands {
    /// SHA-256 of a file (hand-written implementation)
    Hash {
        path: PathBuf,
    },
    /// Create toy XOR container from a file
    Create {
        container: PathBuf,
        password: String,
        #[arg(long)]
        from: PathBuf,
    },
    /// Decrypt toy container and show size + virtual listing
    List {
        container: PathBuf,
        password: String,
    },
}

fn main() -> Result<(), Box<dyn std::error::Error>> {
    let cli = Cli::parse();
    match cli.cmd {
        Commands::Hash { path } => {
            let data = read_file_bytes(&path)?;
            println!("{}", encode_hex(&sha256(&data)));
        }
        Commands::Create {
            container,
            password,
            from,
        } => {
            let data = read_file_bytes(&from)?;
            write_toy(&container, &password, &data)?;
            println!("wrote {} ({} bytes plaintext)", container.display(), data.len());
        }
        Commands::List { container, password } => {
            let plain = open_toy(&container, &password)?;
            for e in list_entries(plain.len()) {
                let slash = if e.is_dir { "/" } else { "" };
                println!("{}{}", e.name, slash);
            }
        }
    }
    Ok(())
}
