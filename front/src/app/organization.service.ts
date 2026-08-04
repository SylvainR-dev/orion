import { Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { Person } from './person.service';
import { HttpClient } from '@angular/common/http';
import { API_BASE_URL } from './config';

@Injectable({ providedIn: 'root' })
export class OrganizationService {

  constructor(private client: HttpClient) {
  }

  async fetchById(id: number): Promise<Organization> {
    const response = this.client.get(`${API_BASE_URL}/api/organizations/${id}`)
    const org = await firstValueFrom(response) as Organization
    org.persons = await this.fetchOrganizationPersons(org.personIds)
    return org
  }

  async fetchAll(): Promise<Organization[]> {
    const response = this.client.get(`${API_BASE_URL}/api/organizations`)
    const organizations = await firstValueFrom(response) as Organization[]
    return organizations.map(org => ({ ...org, persons: [] as Person[] }))
  }

  async fetchOrganizationPersons(personIds: number[]): Promise<Person[]> {
    const persons = await Promise.all(
      personIds.map(id => firstValueFrom(this.client.get(`${API_BASE_URL}/api/persons/${id}`)))
    ) as Person[]
    return persons.map(p => ({ ...p, organizations: [] as Organization[] }))
  }

  async deleteById(id: number): Promise<void> {
    const response = this.client.delete(`${API_BASE_URL}/api/organizations/${id}`)
    await firstValueFrom(response)
    return
  }

  async save(org: Organization): Promise<Organization> {
    let response;
    if (org.id === undefined) {
      response = this.client.post(`${API_BASE_URL}/api/organizations`, {
        name: org.name
      })
    } else {
      response = this.client.put(`${API_BASE_URL}/api/organizations/${org.id}`, {
        name: org.name
      })
    }

    org = await firstValueFrom(response) as Organization
    org.persons = await this.fetchOrganizationPersons(org.personIds)

    return org
  }

  async addPerson(orgId: number, personId: number): Promise<void> {
    const response = this.client.post(`${API_BASE_URL}/api/organizations/${orgId}/persons/${personId}`, null)
    await firstValueFrom(response)
    return
  }

  async removePerson(orgId: number, personId: number): Promise<void> {
    const response = this.client.delete(`${API_BASE_URL}/api/organizations/${orgId}/persons/${personId}`)
    await firstValueFrom(response)
    return
  }

}

export interface Organization {
  id?: number
  name: string
  createdAt: Date
  updatedAt?: Date

  personIds: number[]
  persons: Person[];
}