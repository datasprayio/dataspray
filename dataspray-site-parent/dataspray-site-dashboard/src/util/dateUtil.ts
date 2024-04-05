export const dateToYyyyMmDd = (date: Date): string => {
    return date.toISOString().split('T')[0];
}

export const yyyyMmDdToDate = (yyyyMmDd: string): Date => {
    return new Date(yyyyMmDd);
}
