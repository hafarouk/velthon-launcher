"""zipalign (4-byte) + APK Signature Scheme v2 for an already v1-signed APK, plus a v2 verifier."""
import struct, sys, zlib, hashlib, zipfile
from cryptography.hazmat.primitives.serialization import pkcs12, Encoding, PublicFormat
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.asymmetric import padding


def align(src, dst, alignment=4):
    data = open(src, 'rb').read()
    zf = zipfile.ZipFile(src)
    out = bytearray(); central = bytearray(); count = 0
    for info in zf.infolist():
        off = info.header_offset
        sig, ver, flag, method, mtime, mdate, crc, csize, usize, nlen, xlen = struct.unpack('<IHHHHHIIIHH', data[off:off + 30])
        assert sig == 0x04034b50
        name = data[off + 30:off + 30 + nlen]
        extra = data[off + 30 + nlen:off + 30 + nlen + xlen]
        if flag & 8:  # sizes live in the data descriptor; use the central directory values
            crc, csize, usize = info.CRC, info.compress_size, info.file_size
            flag &= ~8
        body = data[off + 30 + nlen + xlen:off + 30 + nlen + xlen + csize]
        new_off = len(out)
        extra = b''
        if method == 0:
            pad = (alignment - (new_off + 30 + nlen) % alignment) % alignment
            extra = b'\x00' * pad
        out += struct.pack('<IHHHHHIIIHH', 0x04034b50, ver, flag, method, mtime, mdate, crc, csize, usize, nlen, len(extra))
        out += name + extra + body
        # central directory entry
        central += struct.pack('<IHHHHHHIIIHHHHHII', 0x02014b50, info.create_version | (info.create_system << 8),
                               ver, flag, method, mtime, mdate, crc, csize, usize, nlen, 0, 0, 0,
                               info.internal_attr, info.external_attr, new_off) + name
        count += 1
    cd_off = len(out)
    out += central
    out += struct.pack('<IHHHHIIH', 0x06054b50, 0, 0, count, count, len(central), cd_off, 0)
    open(dst, 'wb').write(out)


def lp(b):
    return struct.pack('<I', len(b)) + b


def find_eocd(apk):
    i = apk.rfind(b'PK\x05\x06')
    cd_size, cd_off = struct.unpack('<II', apk[i + 12:i + 20])
    return i, cd_off, cd_size


def content_digest(sections):
    chunks = []
    for sec in sections:
        for i in range(0, len(sec), 1 << 20):
            c = sec[i:i + (1 << 20)]
            chunks.append(hashlib.sha256(b'\xa5' + struct.pack('<I', len(c)) + c).digest())
    return hashlib.sha256(b'\x5a' + struct.pack('<I', len(chunks)) + b''.join(chunks)).digest()


def sign_v2(src, dst, p12, password):
    key, cert, _ = pkcs12.load_key_and_certificates(open(p12, 'rb').read(), password.encode())
    apk = open(src, 'rb').read()
    e, cd_off, cd_size = find_eocd(apk)
    assert cd_off + cd_size == e
    contents, cd, eocd = apk[:cd_off], apk[cd_off:e], apk[e:]
    digest = content_digest([contents, cd, eocd])
    ALGO = 0x0103  # RSASSA-PKCS1-v1_5 with SHA2-256
    digests = lp(lp(struct.pack('<I', ALGO) + lp(digest)))
    certs = lp(lp(cert.public_bytes(Encoding.DER)))
    signed_data = digests + certs + lp(b'')
    sig = key.sign(signed_data, padding.PKCS1v15(), hashes.SHA256())
    signatures = lp(lp(struct.pack('<I', ALGO) + lp(sig)))
    pub = key.public_key().public_bytes(Encoding.DER, PublicFormat.SubjectPublicKeyInfo)
    signer = lp(signed_data) + signatures + lp(pub)
    value = lp(lp(signer))
    pair = struct.pack('<Q', 4 + len(value)) + struct.pack('<I', 0x7109871a) + value
    size = len(pair) + 8 + 16
    block = struct.pack('<Q', size) + pair + struct.pack('<Q', size) + b'APK Sig Block 42'
    new_eocd = eocd[:16] + struct.pack('<I', cd_off + len(block)) + eocd[20:]
    open(dst, 'wb').write(contents + block + cd + new_eocd)


def verify_v2(path):
    from cryptography import x509
    from cryptography.hazmat.primitives.serialization import load_der_public_key
    apk = open(path, 'rb').read()
    e, cd_off, cd_size = find_eocd(apk)
    assert apk[cd_off - 16:cd_off] == b'APK Sig Block 42', 'no signing block'
    size = struct.unpack('<Q', apk[cd_off - 24:cd_off - 16])[0]
    start = cd_off - size - 8
    assert struct.unpack('<Q', apk[start:start + 8])[0] == size
    pairs = apk[start + 8:cd_off - 24]
    p = 0; v2 = None
    while p < len(pairs):
        ln = struct.unpack('<Q', pairs[p:p + 8])[0]; pid = struct.unpack('<I', pairs[p + 8:p + 12])[0]
        if pid == 0x7109871a: v2 = pairs[p + 12:p + 8 + ln]
        p += 8 + ln
    def rd(b, o):
        n = struct.unpack('<I', b[o:o + 4])[0]; return b[o + 4:o + 4 + n], o + 4 + n
    signers, _ = rd(v2, 0); signer, _ = rd(signers, 0)
    sd, o = rd(signer, 0); sigs, o = rd(signer, o); pub, o = rd(signer, o)
    s1, _ = rd(sigs, 0); algo = struct.unpack('<I', s1[:4])[0]; sig, _ = rd(s1, 4)
    load_der_public_key(pub).verify(sig, sd, padding.PKCS1v15(), hashes.SHA256())
    dg, o2 = rd(sd, 0); certs, o2 = rd(sd, o2)
    d1, _ = rd(dg, 0); want, _ = rd(d1, 4)
    c1, _ = rd(certs, 0); cert = x509.load_der_x509_certificate(c1)
    eocd = bytearray(apk[e:]); eocd[16:20] = struct.pack('<I', start)
    got = content_digest([apk[:start], apk[cd_off:e], bytes(eocd)])
    assert got == want, 'content digest mismatch'
    # alignment check for stored entries
    zf = zipfile.ZipFile(path); bad = []
    for info in zf.infolist():
        if info.compress_type == 0:
            h = info.header_offset; nl, xl = struct.unpack('<HH', apk[h + 26:h + 30])
            if (h + 30 + nl + xl) % 4: bad.append(info.filename)
    return algo, cert.subject.rfc4514_string(), bad


if __name__ == '__main__':
    cmd = sys.argv[1]
    if cmd == 'align': align(sys.argv[2], sys.argv[3])
    elif cmd == 'sign': sign_v2(sys.argv[2], sys.argv[3], sys.argv[4], sys.argv[5])
    elif cmd == 'verify': print(verify_v2(sys.argv[2]))
