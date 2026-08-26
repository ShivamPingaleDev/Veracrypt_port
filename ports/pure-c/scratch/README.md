# Educational scratch C lab

**Branch:** `experimental-pure-c` (scratch track)  
Hand-written C11. No VeraCrypt link. Toy crypto only.

## Modules

| File | Topic |
| --- | --- |
| `hex.c` | Hex encode/decode |
| `sha256.c` | SHA-256 from scratch |
| `toy_volume.c` | Magic + length + XOR “container” |
| `main.c` | CLI: `hash`, `create`, `list` |

## Build & test

```bash
cd ports/pure-c/scratch
make test          # toy container (passes)
make test-sha      # after you fix sha256.c / sha256.rs
```

## Advanced lab

Same phones’ `libvc_mobile`: [../vcport-lab/](../vcport-lab/)

## License

Apache-2.0. Toy code is for study only.
