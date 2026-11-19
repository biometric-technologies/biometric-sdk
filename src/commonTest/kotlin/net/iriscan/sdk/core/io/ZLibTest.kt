package net.iriscan.sdk.core.io

import net.iriscan.sdk.io.exception.IOException
import net.iriscan.sdk.testutil.hex
import net.iriscan.sdk.testutil.pseudoRandomBytes
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ZLibTest {

    /**
     * Streams written by the reference zlib (Python 3 zlib module) from the same 3050-byte text
     * of random words; it decompresses to the length and SHA-256 below.
     */
    private val textLength = 3050
    private val textSha256 = "b926071083b6c5472a95455af891f190d52a525e69b3e3524c9c7125ee548d41"

    /** zlib.compress(text, 9): one block with dynamic Huffman codes (RFC 1951, 3.2.7). */
    private val dynamicHuffmanStream = hex(
        """
        78da75565b72c3200cbc0a57736d92788a1d0f71a6cded9b22add083fc0482a5d5ea0979aff752b6bc9fe9ccdb51a633
        a779da729dd2f13cd692caba7d3d1fe952f3fc5d72caaf5cd6253df254e75b9adfba53cde75b89a42fcf5aef3fe9366d
        dbba5f2dc4ba5fea5b78e91bfeb0ac8f73dae7ec6d891cc12863cc4214030f75a0b7da315166e66c54a270d4fb97e220
        e72e12da74d3c0f7f9be8c84d41651e260b22cf43918c13568cdf5759c31565927f4f77c566312476c9218ab302e3964
        233a483926fb037324c5da708a7484250567547b0dd565ded5852e840ea1b6a8ce8635f09ef178b94ea5e4fa62713e24
        c3e4ca00813f503db1b9b5ae0f1f6053040ddf269170c4fb9e0942c6a2993548492d09203270450025905e423e802801
        090141b0563d4fd934e743c09b164c724351943ef18b836554255c4e144bc442cac3c6ce2c2615661c2023ec2ffef61e
        ea89548c4897cf2906ba58c62343d78b33c7944413b1631553d860a35d12bae420559cee47418ebed83e680ea36dff99
        82229f3195e06393457eb465c05bfd6686f7524cc0929554145711052bdf3941c04c2d9d81f6637dc73f37a05d6ff3e2
        2fb17037354840291fec20570d6481915ff2cb2ab5e88d463fba16e564a6856e69dbd7b6390773cfcde94f45102ed31e
        697b21c4410007b19af4d8b1c918a357c0a0acc34dd96ffad6c42ec90acc5f5e9e7bf0960574f79839e2c3bc84c0bab7
        008f99ee94b52093efb36ee80799ca7a82896776a2ea49856f1fc978030236e2ea53c8ff7dcefc348e6563bb6440d2b7
        a97f84dad26238b1e30b3d5cb4b0e8c780b9c1edfbca362fe613df539d78e80c3c17836f7aaae9f45010ff005c6a8ac9
        """
    )

    /** compressobj with strategy Z_FIXED: one block with the fixed Huffman code (3.2.6). */
    private val fixedHuffmanStream = hex(
        """
        78014bcd2bcacfc9c94dcd2b512849cd2dc8492c4955484ecc4d2d4a5428282dc8cc51c8c9cc4d2a2d56482b4a4dcece
        495548ad4ccdc94c51284e4d2c4ace504806ea4d2c4a2d016a82a84e2b2d2aca2f57c848cccdcdcc4b473522332fad08
        a83805c1804aa464169724e625a7a2db0557073106c932a82be01a31dc812480cc44f6185c33d4e5504be1a15050949f
        84e406b8385a48205b0dd601934fce4fc1a60889090b25686042d5c2f4430303c36b305dc9459505259861958a1ca115
        25a5452856c284a056425c8c148c29a918b181e941481c43ecc7621d44155437cc53103d70574202075bda039b8a16f3
        68e9023921208c4062c25227d82c2cbe879a07a5d2137372528b2aa1caa182108b215ec162025402929ea0d665166516
        a307304a22009b8f1a891073e0be47c404c4641885ec32b091f0a8852880850ccc2b7003e10189ae022e017328c420b8
        03e026a0da8aee4e3803ec798c0007eb825909cd509050c2e53ecc82055b2a8126274858c2c2029e3c50c30e8542890a
        94e200162350ffc2b8883c848848241741f442c52161809c58b01719c8e905cd3aa893e03a616107d58292b061ae41f6
        12dcb9100f42521c727e849b8ce917d47c00f6302cdb825c0a7322540cea140c3f82d5c2e207d96698f1a8fac1d640d9
        f0c404330b4e43b420b915ae14e62af49c83a100a5d4428e013081ea77180fad8046cbdb500abd12c3a89bc046c28c42
        f2036a418e9481500d86c52fc45fa89ac0a187ade887e55a587242292d90b3346abe46cd9c58ca3db4721a5722c0a84c
        11218d5a21601604300fc26894e8412d36a166606b056049d6183525a2a6076762b44846320cbdf242773b866fa10a90
        730f4a39821ecc2918018bd616801633084fa1da002ff970ebc5c80ff05219b90483fb0cb544452ea96072381d836e01
        dc306c6e458f42281f3dced04b63cc64839a4bb038123d9ba23742519316d438b83de8091da3a285d9885e0ca0d4e0a8
        ed2bd4cc0b2b9fa0f514c2e1183903d65cc4f01b72a9861c3d904004005c6a8ac9
        """
    )

    /** compressobj(6) flushed with Z_SYNC_FLUSH and Z_FULL_FLUSH: several blocks, including empty stored ones. */
    private val multiBlockStream = hex(
        """
        789c6c515b0ec33008bb0a57a3095da3918728d1d6db4fea6846dbfd21b0b13154a432672a0a4ab9312a41c04c82d07a
        4b0c9cf2d4579885c2930968234e115642090b84ca8c42aa64e8b98bd4172c98732a8ff38a546641a1f82b6c10d3aa58
        025db506eebbc689998b41bcf9700d5ffac306d99c9be848a1499d9c87d1bf24e1a577c6310f35fe03b9f248c9c234ec
        c1b7306ea71dac205bd37b56e41ffad62e27c9bdf5010000ffff7454cb0ec3200cfbd6494353a59ed8697f3f0d3f4842
        76a2a5c4b11d9739d4128c838dcf714ce3148819a37fd30ea7582d51a8314b98d3656fa196c9975cc4206c88f0a8742e
        ac463df1b8bc1ef73de687c7b989c690d220f003f2c476d7bcded5e01482859f87081cabdf9300b296c86c417ab43820
        6724c58036b29ef00711059009182177ad3cfdb0c41f86af2ab5e40f0597fef13b2f962e258c13bc94178e47f62e2d69
        14e93ad044a857affb1fda830c8c50cb7d7810c3d25f19312fa51d29b952deb124055b6ca224d3fdbd7d010000ffff7d
        545b72c3200cbc0a572358499882ed91f1b4be7da7b0122fa73fc680b4dad503e3f8da93d9797b90093e3ecec3248a7b
        b0890c5d14fc6268e52d84486b322f1b02f1658a97db1632cf9379fb369efd61de3646bfbee4ec20cbee5df19e4cee2b
        50b1854d1759e07bff1c06ff8b3f925d5dc5d2b5b8345cd55458e981b391d8ce06c028e2103073cd9f5ebbecc457b5fe
        a493952e16c8d380486c25942105aad12047c520d3e8d8011878d0d53be5ecf9f5c996a9ab257ce124c4b195cb3fe7fc
        d1226612ba735b0896292552847ad7329d9a40d61e387f2007e5a806224204cada95470e4b6301a366baf2bd696b6d50
        89a4b1f773f7612c72032653208e23f7492d0cdae9a9b0cb9ce6654aac6409c8856123aa8f2066fff84ef3206901f450
        9542b69b697d40cadd4732630005bbe33a9610fbb1668239b561edfd6e4a6e488e63aa9eb8e85b0b701a676cf42a0e07
        12717c0640a819d7a65ddbe195f7a9d837c4a7c90097595bfbaab5e52949fc055c6a8ac9
        """
    )

    private fun assertIsTheText(bytes: ByteArray) {
        assertEquals(textLength, bytes.size)
        assertEquals(textSha256, SHA256.digest(bytes).toHexLower())
    }

    @Test
    fun readsDynamicHuffmanBlocks() = assertIsTheText(ZLib.uncompress(dynamicHuffmanStream))

    @Test
    fun readsFixedHuffmanBlocks() = assertIsTheText(ZLib.uncompress(fixedHuffmanStream))

    @Test
    fun readsSeveralBlocksAndEmptyStoredBlocks() = assertIsTheText(ZLib.uncompress(multiBlockStream))

    @Test
    fun readsShortStreamFromReferenceZlib() {
        // python: zlib.compress(b"hello hello hello"), with a back-reference that overlaps itself
        val stream = hex("789ccb48cdc9c957c84090003a2e067d")
        assertEquals("hello hello hello", ZLib.uncompress(stream).decodeToString())
    }

    @Test
    fun readsStoredBlockWrittenByHand() {
        // header 78 01, stored block: BFINAL=1 BTYPE=00, LEN=3, NLEN=~3, "abc", Adler-32 of "abc"
        val stream = hex("7801" + "01" + "0300" + "fcff" + "616263" + "024d0127")
        assertEquals("abc", ZLib.uncompress(stream).decodeToString())
    }

    @Test
    fun roundTripsTypicalInputs() {
        val samples = listOf(
            "empty" to ByteArray(0),
            "one byte" to byteArrayOf(42),
            "short text" to "iris iris iris iris iris".encodeToByteArray(),
            "long runs" to ByteArray(200_000) { (it % 97).toByte() },
            "zeros, longer than the 32 KiB window" to ByteArray(100_000),
            "noise, stored uncompressed" to pseudoRandomBytes(80_000, seed = 3),
            "noise, more than one stored block" to pseudoRandomBytes(150_000, seed = 4),
        )
        for ((name, data) in samples) {
            assertContentEquals(data, ZLib.uncompress(ZLib.compress(data)), name)
        }
    }

    @Test
    fun compressesRepetitiveData() {
        val data = ByteArray(100_000) { (it % 64).toByte() }
        assertTrue(ZLib.compress(data).size < data.size / 50)
    }

    @Test
    fun storesIncompressibleDataWithMinimalOverhead() {
        val noise = pseudoRandomBytes(100_000, seed = 9)
        // zlib header 2 + Adler-32 4 + 5 bytes for each of the 2 stored blocks (65535 + 34465 bytes)
        assertEquals(noise.size + 2 + 4 + 2 * 5, ZLib.compress(noise).size)
    }

    @Test
    fun writesStandardHeaderAndChecksum() {
        val stream = ZLib.compress("Wikipedia".encodeToByteArray())
        assertEquals(0x78, stream[0].toInt() and 0xFF)
        assertEquals(0x9C, stream[1].toInt() and 0xFF)
        assertEquals(0, ((stream[0].toInt() and 0xFF) * 256 + (stream[1].toInt() and 0xFF)) % 31)
        assertContentEquals(hex("11e60398"), stream.copyOfRange(stream.size - 4, stream.size))
    }

    @Test
    fun rejectsInvalidHeaders() {
        val good = ZLib.compress("abc".encodeToByteArray())
        val cases = mapOf(
            "too short" to ByteArray(3),
            "compression method 7" to good.copyOf().also { it[0] = 0x77 },
            "check bits" to good.copyOf().also { it[1] = (it[1] + 1).toByte() },
            "preset dictionary" to good.copyOf().also { it[1] = 0xBB.toByte() }, // 0x78BB is a multiple of 31, FDICT set
        )
        for ((name, data) in cases) assertFailsWith<IOException>(name) { ZLib.uncompress(data) }
    }

    @Test
    fun rejectsCorruptOrTruncatedData() {
        val stream = ZLib.compress(pseudoRandomBytes(5_000, seed = 1) + ByteArray(5_000))
        val cases = mapOf(
            "truncated" to stream.copyOf(stream.size / 2),
            "checksum" to stream.copyOf().also { it[it.size - 1] = (it[it.size - 1] + 1).toByte() },
            "missing checksum" to stream.copyOf(stream.size - 4) + ByteArray(1),
            "reserved block type 3" to hex("7801" + "07" + "000000000000"),
            "stored length check" to hex("7801" + "01" + "0300" + "0000" + "616263" + "024d0127"),
            // fixed block whose first symbol is a match of length 3 at distance 1: nothing to copy from yet
            "distance before the start" to hex("789c" + "0302" + "00" + "00000000"),
        )
        for ((name, data) in cases) assertFailsWith<IOException>(name) { ZLib.uncompress(data) }
    }
}
