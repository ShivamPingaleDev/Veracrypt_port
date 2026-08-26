//! Educational scratch Rust lab — hand-written building blocks.
//! Not VeraCrypt. Not secure for real data.

pub mod hex;
pub mod sha256;
pub mod toy_container;

pub use hex::{decode_hex, encode_hex};
pub use sha256::sha256;
pub use toy_container::{list_entries, open_toy, read_file_bytes, write_toy, ToyEntry, ToyError};
