//! Toy “container” — magic + length + XOR payload. Teaching structure only.

use std::fs::{self, File};
use std::io::{Read, Write};
use std::path::Path;

use thiserror::Error;

pub const MAGIC: [u8; 8] = *b"VCEDU1\0\0";

#[derive(Debug, Error)]
pub enum ToyError {
    #[error("io: {0}")]
    Io(#[from] std::io::Error),
    #[error("bad magic")]
    BadMagic,
    #[error("password required")]
    NoPassword,
    #[error("payload too large")]
    TooLarge,
}

#[derive(Debug, Clone)]
pub struct ToyEntry {
    pub name: String,
    pub is_dir: bool,
}

fn stream_key(password: &str) -> Vec<u8> {
    let mut key = vec![0u8; 32];
    for (i, b) in password.bytes().enumerate() {
        key[i % 32] ^= b;
    }
    for i in 0..32 {
        key[i] = key[i].wrapping_add((i as u8) ^ 0x5c);
    }
    key
}

fn xor_bytes(data: &[u8], key: &[u8]) -> Vec<u8> {
    data.iter()
        .enumerate()
        .map(|(i, b)| b ^ key[i % key.len()])
        .collect()
}

pub fn write_toy(path: &Path, password: &str, plaintext: &[u8]) -> Result<(), ToyError> {
    if password.is_empty() {
        return Err(ToyError::NoPassword);
    }
    if plaintext.len() > 1024 * 1024 {
        return Err(ToyError::TooLarge);
    }
    let key = stream_key(password);
    let body = xor_bytes(plaintext, &key);
    let mut file = File::create(path)?;
    file.write_all(&MAGIC)?;
    file.write_all(&(body.len() as u32).to_le_bytes())?;
    file.write_all(&body)?;
    Ok(())
}

pub fn open_toy(path: &Path, password: &str) -> Result<Vec<u8>, ToyError> {
    if password.is_empty() {
        return Err(ToyError::NoPassword);
    }
    let mut file = File::open(path)?;
    let mut magic = [0u8; 8];
    file.read_exact(&mut magic)?;
    if magic != MAGIC {
        return Err(ToyError::BadMagic);
    }
    let mut len_buf = [0u8; 4];
    file.read_exact(&mut len_buf)?;
    let len = u32::from_le_bytes(len_buf) as usize;
    let mut body = vec![0u8; len];
    file.read_exact(&mut body)?;
    let key = stream_key(password);
    Ok(xor_bytes(&body, &key))
}

/// Toy “list” — one virtual file holding the whole payload name.
pub fn list_entries(plaintext_len: usize) -> Vec<ToyEntry> {
    vec![ToyEntry {
        name: format!("payload.bin ({} bytes)", plaintext_len),
        is_dir: false,
    }]
}

pub fn read_file_bytes(path: &Path) -> Result<Vec<u8>, ToyError> {
    Ok(fs::read(path)?)
}

#[cfg(test)]
mod tests {
    use super::*;
    use tempfile::NamedTempFile;

    #[test]
    fn roundtrip() {
        let tmp = NamedTempFile::new().unwrap();
        let plain = b"hello educational rust";
        write_toy(tmp.path(), "pw", plain).unwrap();
        let back = open_toy(tmp.path(), "pw").unwrap();
        assert_eq!(back, plain);
    }

    #[test]
    fn wrong_password_garbage() {
        let tmp = NamedTempFile::new().unwrap();
        write_toy(tmp.path(), "right", b"data").unwrap();
        let wrong = open_toy(tmp.path(), "wrong").unwrap();
        assert_ne!(wrong, b"data");
    }
}
