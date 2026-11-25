#!/usr/bin/env python3
"""Standalone Python implementation of cn.omix.security.SafeStorage."""

import argparse
import secrets
from pathlib import Path


NORMAL_PREFIX = "safe:v1:normal:"
HEAVY_PREFIX = "safe:v1:heavy:"
TWO_BIT_ALPHABET = "lOI0"
HEAVY_PADDING_ALPHABET = "123456789ABCDEF"
HEAVY_XOR_KEY = b"8964"


def _encode_two_bit(data):
    return "".join(
        TWO_BIT_ALPHABET[(byte >> shift) & 3]
        for byte in data
        for shift in (6, 4, 2, 0)
    )


def _decode_two_bit(encoded):
    if len(encoded) % 4:
        raise ValueError("Safe Storage payload length must be divisible by four.")
    if any(char not in TWO_BIT_ALPHABET for char in encoded):
        raise ValueError("Invalid character in Safe Storage payload.")
    return bytes(
        sum(TWO_BIT_ALPHABET.index(encoded[index + pair]) << (6 - pair * 2)
            for pair in range(4))
        for index in range(0, len(encoded), 4)
    )


def _xor_heavy_key(data):
    return bytes(byte ^ HEAVY_XOR_KEY[index % len(HEAVY_XOR_KEY)]
                 for index, byte in enumerate(data))


def _add_heavy_padding(encoded):
    if not encoded:
        return ""
    result = [encoded[0]]
    for char in encoded[1:]:
        result.extend(secrets.choice(HEAVY_PADDING_ALPHABET)
                      for _ in range(5 + secrets.randbelow(8)))
        result.append(char)
    return "".join(result)


def _remove_heavy_padding(padded):
    if not padded:
        return ""
    if padded[0] not in TWO_BIT_ALPHABET:
        raise ValueError("Heavy Safe Storage payload must begin with encoded data.")
    encoded = []
    padding_length = 0
    for char in padded:
        if char in TWO_BIT_ALPHABET:
            if encoded and not 5 <= padding_length <= 12:
                raise ValueError("Heavy Safe Storage padding must contain 5 to 12 characters.")
            encoded.append(char)
            padding_length = 0
        elif char in HEAVY_PADDING_ALPHABET:
            padding_length += 1
        else:
            raise ValueError("Invalid character in heavy Safe Storage padding.")
    if padding_length:
        raise ValueError("Heavy Safe Storage payload cannot end with padding.")
    return "".join(encoded)


def _decode_utf8(data):
    try:
        return data.decode("utf-8", errors="strict")
    except UnicodeDecodeError as exc:
        raise ValueError("Safe Storage payload is not valid UTF-8.") from exc


def _decode_normal(payload):
    return _decode_utf8(_decode_two_bit(payload))


def _decode_heavy(payload):
    return _decode_utf8(_xor_heavy_key(_decode_two_bit(_remove_heavy_padding(payload))))


def _plausible_plaintext(value):
    return all(not (ord(char) <= 0x1f or 0x7f <= ord(char) <= 0x9f)
               or char in "\n\r\t" for char in value)


def _heuristic_mode(value):
    if not value:
        return None
    try:
        if _plausible_plaintext(_decode_heavy(value)):
            return "heavy"
    except ValueError:
        pass
    if len(value) % 4 or any(char not in TWO_BIT_ALPHABET for char in value):
        return None
    try:
        if _plausible_plaintext(_decode_normal(value)):
            return "normal"
    except ValueError:
        pass
    return None


def encrypt(plaintext, mode="heavy"):
    """Return the same unprefixed storage format as SafeStorage.encrypt."""
    data = plaintext.encode("utf-8")
    if mode == "normal":
        return _encode_two_bit(data)
    if mode == "heavy":
        return _add_heavy_padding(_encode_two_bit(_xor_heavy_key(data)))
    raise ValueError("Mode must be 'normal' or 'heavy'.")


def decrypt(stored_value):
    """Decode current or legacy values; pass through unrecognized plaintext."""
    if stored_value is None:
        return None
    if stored_value.startswith(NORMAL_PREFIX):
        return _decode_normal(stored_value[len(NORMAL_PREFIX):])
    if stored_value.startswith(HEAVY_PREFIX):
        return _decode_heavy(stored_value[len(HEAVY_PREFIX):])
    if stored_value.startswith("safe:"):
        raise ValueError("Unsupported Safe Storage format.")
    mode = _heuristic_mode(stored_value)
    if mode == "normal":
        return _decode_normal(stored_value)
    if mode == "heavy":
        return _decode_heavy(stored_value)
    return stored_value


def _run(action, mode="heavy"):
    if action == "encrypt":
        print("结果：" + encrypt(input("请输入明文："), mode))
    else:
        print("结果：" + decrypt(input("请输入待解密内容：")))


def process_file(action, input_path, output_path=None, mode="heavy"):
    """Process one complete UTF-8 file, preserving plaintext line endings."""
    source = Path(input_path)
    destination = Path(output_path or str(source) + (".encrypted" if action == "encrypt" else ".decrypted"))
    content = source.read_bytes().decode("utf-8")
    result = encrypt(content, mode) if action == "encrypt" else decrypt(content)
    with destination.open("xb") as output:
        output.write(result.encode("utf-8"))
    return destination


def main():
    parser = argparse.ArgumentParser(description="与 Omix SafeStorage 兼容的加密/解密工具")
    subparsers = parser.add_subparsers(dest="action")
    encrypt_parser = subparsers.add_parser("encrypt", help="手动输入明文并加密")
    encrypt_parser.add_argument("--mode", choices=("normal", "heavy"), default="heavy")
    decrypt_parser = subparsers.add_parser("decrypt", help="手动输入存储值并解密")
    for command in (encrypt_parser, decrypt_parser):
        command.add_argument("--file", help="读取整个 UTF-8 文件；省略时手动输入一行")
        command.add_argument("--output", help="输出文件路径；默认在输入路径后加 .encrypted 或 .decrypted")
    args = parser.parse_args()
    if args.action:
        if args.output and not args.file:
            parser.error("--output 只能与 --file 一起使用")
        try:
            if args.file:
                result_path = process_file(args.action, args.file, args.output, getattr(args, "mode", "heavy"))
                print("已写入：{}".format(result_path))
            else:
                _run(args.action, getattr(args, "mode", "heavy"))
        except (OSError, ValueError) as exc:
            parser.exit(1, "错误：{}\n".format(exc))
        return

    while True:
        choice = input("\n1. 输入加密  2. 输入解密  3. 文件加密  4. 文件解密  0. 退出\n请选择：").strip()
        if choice == "0":
            return
        if choice in ("1", "3"):
            mode = input("模式（normal/heavy，回车默认 heavy）：").strip().lower() or "heavy"
            if mode not in ("normal", "heavy"):
                print("错误：模式必须是 normal 或 heavy。")
                continue
            action = "encrypt"
        elif choice in ("2", "4"):
            action, mode = "decrypt", "heavy"
        else:
            print("错误：请输入 0、1、2、3 或 4。")
            continue
        try:
            if choice in ("3", "4"):
                source = input("输入文件路径：").strip()
                destination = input("输出文件路径（回车自动命名）：").strip() or None
                print("已写入：{}".format(process_file(action, source, destination, mode)))
            else:
                _run(action, mode)
        except (OSError, ValueError) as exc:
            print("错误：{}".format(exc))


if __name__ == "__main__":
    try:
        main()
    except (EOFError, KeyboardInterrupt):
        print()
