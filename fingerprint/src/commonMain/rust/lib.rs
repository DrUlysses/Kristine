//! Chromaprint fingerprints of audio files, computed like `fpcalc` does by default: the first 120 seconds, the
//! TEST2 algorithm, and the compressed form base64-encoded (URL-safe, no padding) as AcoustID expects it.

use base64::prelude::{Engine, BASE64_URL_SAFE_NO_PAD};
use rusty_chromaprint::{Configuration, FingerprintCompressor, Fingerprinter};
use symphonia::core::audio::SampleBuffer;
use symphonia::core::codecs::{DecoderOptions, CODEC_TYPE_NULL};
use symphonia::core::errors::Error;
use symphonia::core::formats::FormatOptions;
use symphonia::core::io::MediaSourceStream;
use symphonia::core::meta::MetadataOptions;
use symphonia::core::probe::Hint;
use std::path::Path;

/// Like fpcalc's default; stored prints from fpcalc stay comparable
const MAX_SECONDS: u64 = 120;

#[derive(uniffi::Record)]
pub struct Fingerprint {
    /// The whole file's length
    pub duration_seconds: u32,
    /// One value per about 0.12 s of sound, for comparing two files
    pub raw: Vec<i32>,
    /// For AcoustID
    pub compressed: String,
}

/// The fingerprint of the audio file at `path`, or None when it can't be read or decoded (WMA, Opus).
#[uniffi::export]
pub fn fingerprint(path: String) -> Option<Fingerprint> {
    compute(Path::new(&path)).ok()
}

fn compute(path: &Path) -> Result<Fingerprint, Error> {
    let file = std::fs::File::open(path)?;
    let stream = MediaSourceStream::new(Box::new(file), Default::default());
    let mut hint = Hint::new();
    if let Some(extension) = path.extension().and_then(|it| it.to_str()) {
        hint.with_extension(extension);
    }
    let mut format = symphonia::default::get_probe()
        .format(&hint, stream, &FormatOptions::default(), &MetadataOptions::default())?
        .format;
    let track = format
        .tracks()
        .iter()
        .find(|it| it.codec_params.codec != CODEC_TYPE_NULL)
        .ok_or(Error::Unsupported("no audio track"))?;
    let track_id = track.id;
    let known_frames = track.codec_params.n_frames;
    let mut decoder = symphonia::default::get_codecs().make(&track.codec_params, &DecoderOptions::default())?;

    let config = Configuration::preset_test2();
    let mut printer = Fingerprinter::new(&config);
    // Rate and channels come from the decoded audio: an MP4 track's header may not name its channels
    let mut sample_rate: u32 = 0;
    let mut channels: usize = 0;
    let mut frames: u64 = 0;
    let mut samples: Option<SampleBuffer<i16>> = None;
    loop {
        let packet = match format.next_packet() {
            Ok(packet) => packet,
            // The end of the file, or a stream that can't be read further
            Err(_) => break,
        };
        if packet.track_id() != track_id {
            continue;
        }
        let decoded = match decoder.decode(&packet) {
            Ok(decoded) => decoded,
            // A damaged packet is skipped, like fpcalc does
            Err(Error::DecodeError(_)) => continue,
            Err(_) => break,
        };
        let count = decoded.frames() as u64;
        if samples.is_none() {
            let spec = *decoded.spec();
            sample_rate = spec.rate;
            channels = spec.channels.count();
            printer
                .start(sample_rate, channels as u32)
                .map_err(|_| Error::Unsupported("sample rate or channels"))?;
        }
        let limit = MAX_SECONDS * u64::from(sample_rate);
        if frames < limit {
            let buffer = samples.get_or_insert_with(|| SampleBuffer::new(decoded.capacity() as u64, *decoded.spec()));
            buffer.copy_interleaved_ref(decoded);
            let wanted = count.min(limit - frames) as usize;
            printer.consume(&buffer.samples()[..wanted * channels]);
        } else if known_frames.is_some() {
            // The container knows the length: the rest needn't be decoded
            break;
        }
        frames += count;
    }
    if sample_rate == 0 {
        return Err(Error::Unsupported("no audio"));
    }
    printer.finish();
    let raw: Vec<i32> = printer.fingerprint().iter().map(|it| *it as i32).collect();
    let compressed = BASE64_URL_SAFE_NO_PAD.encode(FingerprintCompressor::from(&config).compress(printer.fingerprint()));
    Ok(Fingerprint {
        duration_seconds: (known_frames.unwrap_or(frames) / u64::from(sample_rate)) as u32,
        raw,
        compressed,
    })
}

uniffi::setup_scaffolding!();
