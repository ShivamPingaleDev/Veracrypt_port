//! Hand-written hex encode/decode (no `hex` crate in default build).

const HEX: &[u8; 16] = b"0123456789abcdef";

pub fn encode_hex(bytes: &[u8]) -> String {
    let mut out = String::with_capacity(bytes.len() * 2);
    for b in bytes {
        out.push(HEX[(b >> 4) as usize] as char);
        out.push(HEX[(b & 0x0f) as usize] as char);
    }
    out
}

pub fn decode_hex(s: &str) -> Result<Vec<u8>, String> {
    let t = s.trim();
    if t.len() % 2 != 0 {
        return Err("hex length must be even".into());
    }
    let mut out = Vec::with_capacity(t.len() / 2);
    let bytes = t.as_bytes();
    let mut i = 0;
    while i < bytes.len() {
        let hi = from_hex_digit(bytes[i])?;
        let lo = from_hex_digit(bytes[i + 1])?;
        out.push((hi << 4) | lo);
        i += 2;
    }
    Ok(out)
}

fn from_hex_digit(c: u8) -> Result<u8, String> {
    match c {
        b'0'..=b'9' => Ok(c - b'0'),
        b'a'..=b'f' => Ok(c - b'a' + 10),
        b'A'..=b'F' => Ok(c - b'A' + 10),
        _ => Err(format!("invalid hex digit: {}", c as char)),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn roundtrip() {
        let raw = b"VC Port edu";
        assert_eq!(decode_hex(&encode_hex(raw)).unwrap(), raw);
    }
}
