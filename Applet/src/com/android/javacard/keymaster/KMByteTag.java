/*
 * Copyright(C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.javacard.keymaster;

import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.Util;

/**
 * KMByteTag represents BYTES Tag Type from android keymaster hal specifications. The tag value of
 * this tag is the KMByteBlob pointer i.e. offset of KMByteBlob in memory heap. struct{byte
 * TAG_TYPE; short length; struct{short BYTES_TAG; short tagKey; short blobPtr}}
 */

public class KMByteTag extends KMTag {

  private static KMByteTag prototype;

  private KMByteTag() {
  }

  private static KMByteTag proto(short ptr) {
    if (prototype == null) {
      prototype = new KMByteTag();
    }
    KMType.instanceTable[KM_BYTE_TAG_OFFSET] = ptr;
    return prototype;
  }

  // pointer to an empty instance used as expression
  public static short exp() {
    short blobPtr = KMByteBlob.exp();
    short ptr = instance(TAG_TYPE, (short) 6);
    Util.setShort(heap, (short) (ptr + TLV_HEADER_SIZE), BYTES_TAG);
    Util.setShort(heap, (short) (ptr + TLV_HEADER_SIZE + 2), INVALID_TAG);
    Util.setShort(heap, (short) (ptr + TLV_HEADER_SIZE + 4), blobPtr);
    return ptr;
  }

  public static short instance(short key, short byteBlob) {
    if (!validateKey(key, byteBlob)) {
      ISOException.throwIt(ISO7816.SW_DATA_INVALID);
    }
    if (heap[byteBlob] != BYTE_BLOB_TYPE) {
      ISOException.throwIt(ISO7816.SW_DATA_INVALID);
    }
    short ptr = instance(TAG_TYPE, (short) 6);
    Util.setShort(heap, (short) (ptr + TLV_HEADER_SIZE), BYTES_TAG);
    Util.setShort(heap, (short) (ptr + TLV_HEADER_SIZE + 2), key);
    Util.setShort(heap, (short) (ptr + TLV_HEADER_SIZE + 4), byteBlob);
    return ptr;
  }

  public static KMByteTag cast(short ptr) {
    if (heap[ptr] != TAG_TYPE) {
      ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
    }
    if (Util.getShort(heap, (short) (ptr + TLV_HEADER_SIZE)) != BYTES_TAG) {
      ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
    }
    return proto(ptr);
  }

  public short getKey() {
    return Util.getShort(heap,
        (short) (KMType.instanceTable[KM_BYTE_TAG_OFFSET] + TLV_HEADER_SIZE + 2));
  }

  public short getTagType() {
    return KMType.BYTES_TAG;
  }

  public short getValue() {
    return Util.getShort(heap,
        (short) (KMType.instanceTable[KM_BYTE_TAG_OFFSET] + TLV_HEADER_SIZE + 4));
  }

  public short length() {
    short blobPtr = Util.getShort(heap,
        (short) (KMType.instanceTable[KM_BYTE_TAG_OFFSET] + TLV_HEADER_SIZE + 4));
    return KMByteBlob.cast(blobPtr).length();
  }

  // TODO Review this function
  private static boolean validateKey(short key, short byteBlob) {
    short valueLen = KMByteBlob.cast(byteBlob).length();
    switch (key) {
      case ATTESTATION_APPLICATION_ID:
        if (valueLen > MAX_ATTESTATION_APP_ID_SIZE) {
          return false;
        }
        break;
      case CERTIFICATE_SUBJECT_NAME:
      {
        KMAsn1Parser asn1Decoder = KMAsn1Parser.instance();
        short length = asn1Decoder.decodeSubject(byteBlob);
        if (length > MAX_SUBJECT_CN_LEN) {
          return false;
        }
      }
        break;
      case APPLICATION_ID:
      case APPLICATION_DATA:
        if (valueLen > MAX_APP_ID_APP_DATA_SIZE) {
          return false;
        }
        break;
      case ATTESTATION_CHALLENGE:
        if (valueLen > MAX_ATTESTATION_CHALLENGE_SIZE) {
          return false;
        }
        break;
      case ATTESTATION_ID_BRAND:
      case ATTESTATION_ID_DEVICE:
      case ATTESTATION_ID_PRODUCT:
      case ATTESTATION_ID_SERIAL:
      case ATTESTATION_ID_IMEI:
      case ATTESTATION_ID_MEID:
      case ATTESTATION_ID_MANUFACTURER:
      case ATTESTATION_ID_MODEL:
        if (valueLen > KMConfigurations.MAX_ATTESTATION_IDS_SIZE) {
          return false;
        }
        break;
      case ROOT_OF_TRUST: // TODO : Not adding it as ByteTag in HiddenParamters.
      //case UNIQUE_ID: This tag never used in keyParamters.
      case NONCE: // Validation of nonce happends in begin operation.
      // Below two tags are obsolete in keymint.
      //case ASSOCIATED_DATA: 
      //case CONFIRMATION_TOKEN:
        break;
      default:
        return false;
    }
    return true;
  }
}
