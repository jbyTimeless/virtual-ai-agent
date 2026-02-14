declare module 'mmd-parser' {
    export class Parser {
        parsePmd(buffer: ArrayBuffer, leftToRight?: boolean): any
        parsePmx(buffer: ArrayBuffer, leftToRight?: boolean): any
        parseVmd(buffer: ArrayBuffer, leftToRight?: boolean): any
        parseVpd(text: string, leftToRight?: boolean): any
    }
    export class CharsetEncoder {
        s2u(uint8Array: Uint8Array): string
    }
    export const MMDParser: {
        Parser: typeof Parser
        CharsetEncoder: typeof CharsetEncoder
    }
}
